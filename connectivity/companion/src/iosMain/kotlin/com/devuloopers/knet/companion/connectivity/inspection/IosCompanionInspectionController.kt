@file:OptIn(kotlinx.cinterop.BetaInteropApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package com.devuloopers.knet.companion.connectivity.inspection

import com.devuloopers.knet.companion.application.contract.CompanionInspectionConfiguration
import com.devuloopers.knet.companion.application.contract.CompanionInspectionController
import com.devuloopers.knet.companion.application.contract.CompanionInspectionPreparationResult
import com.devuloopers.knet.companion.application.contract.CompanionInspectionStartResult
import com.devuloopers.knet.companion.connectivity.transport.IosCompanionProxyTransport
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionInspectionState
import kotlin.coroutines.resume
import kotlin.time.Clock
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.dataWithBytes
import platform.NetworkExtension.NETunnelProviderManager
import platform.NetworkExtension.NETunnelProviderProtocol
import platform.NetworkExtension.NETunnelProviderSession
import platform.NetworkExtension.NEVPNConnection
import platform.NetworkExtension.NEVPNStatusConnected
import platform.NetworkExtension.NEVPNStatusDidChangeNotification
import platform.NetworkExtension.NEVPNStatusDisconnected
import platform.NetworkExtension.NEVPNStatusInvalid
import platform.posix.memcpy

/**
 * iOS Network Extension lifecycle owner. The persisted VPN profile contains no pairing secret; the credential and
 * pinned identities are supplied to the extension only in one start request.
 */
public class IosCompanionInspectionController(
    private val transport: IosCompanionProxyTransport,
    private val nowEpochMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : CompanionInspectionController, AutoCloseable {
    private val lifecycleLock = Mutex()
    private val mutableState = MutableStateFlow<CompanionInspectionState>(CompanionInspectionState.Stopped)
    private var closed: Boolean = false
    private var statusObserver: Any? = null

    override val state: StateFlow<CompanionInspectionState> = mutableState.asStateFlow()

    override suspend fun prepare(): CompanionInspectionPreparationResult = lifecycleLock.withLock {
        check(!closed) { "The iOS inspection controller is closed." }
        observeUnexpectedStatus(null)
        try {
            val manager = loadExistingManager()
            observeUnexpectedStatus(manager?.connection)
            mutableState.value = if (manager?.connection?.status == NEVPNStatusConnected) {
                restoredRunningState(manager)
            } else {
                CompanionInspectionState.Stopped
            }
            CompanionInspectionPreparationResult.Ready
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            val failure = vpnConfigurationFailure()
            mutableState.value = CompanionInspectionState.Failed(failure)
            CompanionInspectionPreparationResult.Failed(failure)
        }
    }

    override suspend fun start(
        configuration: CompanionInspectionConfiguration,
    ): CompanionInspectionStartResult = lifecycleLock.withLock {
        check(!closed) { "The iOS inspection controller is closed." }
        if (mutableState.value is CompanionInspectionState.Running) {
            return@withLock CompanionInspectionStartResult.Started
        }
        val session = transport.sessionSnapshot()
            ?.takeIf { it.registration == configuration.registration }
            ?: return@withLock fail(transportRequiredFailure())

        mutableState.value = CompanionInspectionState.Preparing
        try {
            val manager = loadOrCreateManager()
            configure(manager, configuration)
            manager.save()
            manager.reload()
            val providerSession = manager.connection as? NETunnelProviderSession
                ?: return@withLock fail(vpnConfigurationFailure())
            observeUnexpectedStatus(providerSession)
            val started = startProvider(providerSession, configuration, session.authorizationValue())
            if (!started) return@withLock fail(vpnPermissionFailure())
            val connected = waitForStatus(providerSession, NEVPNStatusConnected, START_TIMEOUT_MILLIS)
            if (!connected) {
                providerSession.stopVPNTunnel()
                return@withLock fail(vpnStartFailure())
            }
            mutableState.value = CompanionInspectionState.Running(
                mode = configuration.mode,
                startedAtEpochMillis = nowEpochMillis(),
                fullHttpsInspection = configuration.fullHttpsInspection,
            )
            CompanionInspectionStartResult.Started
        } catch (cancelled: CancellationException) {
            observeUnexpectedStatus(null)
            mutableState.value = CompanionInspectionState.Stopped
            throw cancelled
        } catch (_: NSErrorException) {
            fail(vpnPermissionFailure())
        } catch (_: Throwable) {
            fail(vpnStartFailure())
        }
    }

    override suspend fun stop(): Unit = lifecycleLock.withLock {
        if (closed) return@withLock
        if (mutableState.value == CompanionInspectionState.Stopped) return@withLock
        mutableState.value = CompanionInspectionState.Stopping
        try {
            loadExistingManager()?.connection?.let { connection ->
                connection.stopVPNTunnel()
                waitForStatus(connection, NEVPNStatusDisconnected, STOP_TIMEOUT_MILLIS)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // Stopping is idempotent. A stale or unavailable system profile is already equivalent to stopped.
        } finally {
            observeUnexpectedStatus(null)
            mutableState.value = CompanionInspectionState.Stopped
        }
    }

    /** Releases the native VPN-status observer owned by this controller. */
    override fun close() {
        if (closed) return
        closed = true
        observeUnexpectedStatus(null)
    }

    private fun observeUnexpectedStatus(connection: NEVPNConnection?) {
        statusObserver?.let(NSNotificationCenter.defaultCenter::removeObserver)
        statusObserver = connection?.let { managedConnection ->
            NSNotificationCenter.defaultCenter.addObserverForName(
                name = NEVPNStatusDidChangeNotification,
                `object` = managedConnection,
                queue = NSOperationQueue.mainQueue,
            ) {
                if (
                    managedConnection.status in setOf(NEVPNStatusDisconnected, NEVPNStatusInvalid) &&
                    mutableState.value is CompanionInspectionState.Running
                ) {
                    mutableState.value = CompanionInspectionState.Failed(vpnStoppedUnexpectedlyFailure())
                }
            }
        }
    }

    private fun fail(failure: CompanionFailure): CompanionInspectionStartResult.Failed {
        observeUnexpectedStatus(null)
        mutableState.value = if (failure.code == CompanionFailureCode.VPN_PERMISSION_DENIED) {
            CompanionInspectionState.AwaitingVpnConsent
        } else {
            CompanionInspectionState.Failed(failure)
        }
        return CompanionInspectionStartResult.Failed(failure)
    }

    private suspend fun loadOrCreateManager(): NETunnelProviderManager =
        loadExistingManager() ?: NETunnelProviderManager()

    private suspend fun loadExistingManager(): NETunnelProviderManager? = withTimeout(PREFERENCES_TIMEOUT_MILLIS) {
        suspendCancellableCoroutine { continuation ->
            NETunnelProviderManager.loadAllFromPreferencesWithCompletionHandler { values, error ->
                if (!continuation.isActive) return@loadAllFromPreferencesWithCompletionHandler
                if (error != null) {
                    continuation.resumeWith(Result.failure(NSErrorException(error)))
                } else {
                    val manager = values.orEmpty()
                        .filterIsInstance<NETunnelProviderManager>()
                        .firstOrNull { candidate ->
                            (candidate.protocolConfiguration as? NETunnelProviderProtocol)
                                ?.providerBundleIdentifier == PACKET_TUNNEL_BUNDLE_IDENTIFIER
                        }
                    continuation.resume(manager)
                }
            }
        }
    }

    private fun configure(
        manager: NETunnelProviderManager,
        configuration: CompanionInspectionConfiguration,
    ) {
        val protocol = NETunnelProviderProtocol().apply {
            providerBundleIdentifier = PACKET_TUNNEL_BUNDLE_IDENTIFIER
            serverAddress = DISPLAY_SERVER_ADDRESS
            providerConfiguration = mapOf(
                TunnelOptionKey.SCHEMA_VERSION to TUNNEL_SCHEMA_VERSION,
                TunnelOptionKey.PRODUCT to TUNNEL_PRODUCT,
                TunnelOptionKey.MODE to configuration.mode.name,
                TunnelOptionKey.FULL_HTTPS_INSPECTION to configuration.fullHttpsInspection.toString(),
            )
        }
        manager.localizedDescription = TUNNEL_PRODUCT
        manager.protocolConfiguration = protocol
        manager.enabled = true
    }

    private fun restoredRunningState(manager: NETunnelProviderManager): CompanionInspectionState.Running {
        val configuration = (manager.protocolConfiguration as? NETunnelProviderProtocol)
            ?.providerConfiguration
            .orEmpty()
        val mode = (configuration[TunnelOptionKey.MODE] as? String)
            ?.let { stored ->
                com.devuloopers.knet.companion.model.CompanionInspectionMode.entries
                    .firstOrNull { it.name == stored }
            }
            ?: com.devuloopers.knet.companion.model.CompanionInspectionMode.DEVICE_VPN
        val fullHttpsInspection = configuration[TunnelOptionKey.FULL_HTTPS_INSPECTION] == "true"
        return CompanionInspectionState.Running(
            mode = mode,
            startedAtEpochMillis = nowEpochMillis(),
            fullHttpsInspection = fullHttpsInspection,
        )
    }

    private suspend fun NETunnelProviderManager.save(): Unit = withTimeout(PREFERENCES_TIMEOUT_MILLIS) {
        suspendCancellableCoroutine { continuation ->
            saveToPreferencesWithCompletionHandler { error ->
                if (!continuation.isActive) return@saveToPreferencesWithCompletionHandler
                if (error == null) continuation.resume(Unit)
                else continuation.resumeWith(Result.failure(NSErrorException(error)))
            }
        }
    }

    private suspend fun NETunnelProviderManager.reload(): Unit = withTimeout(PREFERENCES_TIMEOUT_MILLIS) {
        suspendCancellableCoroutine { continuation ->
            loadFromPreferencesWithCompletionHandler { error ->
                if (!continuation.isActive) return@loadFromPreferencesWithCompletionHandler
                if (error == null) continuation.resume(Unit)
                else continuation.resumeWith(Result.failure(NSErrorException(error)))
            }
        }
    }

    private fun startProvider(
        session: NETunnelProviderSession,
        configuration: CompanionInspectionConfiguration,
        authorizationValue: String,
    ): Boolean {
        val registration = configuration.registration
        val options: Map<Any?, Any> = mapOf(
            TunnelOptionKey.SCHEMA_VERSION to TUNNEL_SCHEMA_VERSION,
            TunnelOptionKey.DESKTOP_ID to registration.desktopId.value,
            TunnelOptionKey.PROXY_HOST to registration.proxyEndpoint.host,
            TunnelOptionKey.PROXY_PORT to registration.proxyEndpoint.port,
            TunnelOptionKey.AUTHORIZATION to authorizationValue,
            TunnelOptionKey.ROOT_CERTIFICATE to registration.rootCertificate.copyBytes().base64(),
            TunnelOptionKey.ROOT_SHA256 to registration.rootCertificateSha256.value,
            TunnelOptionKey.TRANSPORT_SHA256 to registration.transportIdentitySha256.value,
            TunnelOptionKey.UNSUPPORTED_POLICY to configuration.unsupportedTrafficPolicy.name,
        )
        return memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            error.value = null
            session.startTunnelWithOptions(options, error.ptr).also { accepted ->
                if (!accepted) error.value?.let { failure -> throw NSErrorException(failure) }
            }
        }
    }

    private suspend fun waitForStatus(
        connection: platform.NetworkExtension.NEVPNConnection,
        expected: Long,
        timeoutMillis: Long,
    ): Boolean {
        if (connection.status == expected) return true
        return withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                var token: Any? = null
                fun finish(value: Boolean) {
                    val observer = token ?: return
                    NSNotificationCenter.defaultCenter.removeObserver(observer)
                    token = null
                    if (continuation.isActive) continuation.resume(value)
                }
                token = NSNotificationCenter.defaultCenter.addObserverForName(
                    name = NEVPNStatusDidChangeNotification,
                    `object` = connection,
                    queue = NSOperationQueue.mainQueue,
                ) {
                    when (connection.status) {
                        expected -> finish(true)
                        NEVPNStatusInvalid -> finish(false)
                        NEVPNStatusDisconnected -> if (expected != NEVPNStatusDisconnected) finish(false)
                    }
                }
                continuation.invokeOnCancellation {
                    token?.let(NSNotificationCenter.defaultCenter::removeObserver)
                    token = null
                }
            }
        } ?: false
    }

    private class NSErrorException(val error: NSError) : Exception(error.localizedDescription)

    private companion object {
        private const val PACKET_TUNNEL_BUNDLE_IDENTIFIER: String =
            "com.devuloopers.knet.companion.PacketTunnel"
        private const val DISPLAY_SERVER_ADDRESS: String = "KNet Desktop"
        private const val TUNNEL_PRODUCT: String = "KNet Companion Inspection"
        private const val TUNNEL_SCHEMA_VERSION: String = "1"
        private const val START_TIMEOUT_MILLIS: Long = 15_000L
        private const val STOP_TIMEOUT_MILLIS: Long = 5_000L
        private const val PREFERENCES_TIMEOUT_MILLIS: Long = 10_000L
    }
}

/** Keys shared with the native extension. Values carrying secrets are start options only. */
internal object TunnelOptionKey {
    const val SCHEMA_VERSION: String = "schemaVersion"
    const val PRODUCT: String = "product"
    const val MODE: String = "mode"
    const val FULL_HTTPS_INSPECTION: String = "fullHttpsInspection"
    const val DESKTOP_ID: String = "desktopId"
    const val PROXY_HOST: String = "proxyHost"
    const val PROXY_PORT: String = "proxyPort"
    const val AUTHORIZATION: String = "authorization"
    const val ROOT_CERTIFICATE: String = "rootCertificate"
    const val ROOT_SHA256: String = "rootSha256"
    const val TRANSPORT_SHA256: String = "transportSha256"
    const val UNSUPPORTED_POLICY: String = "unsupportedPolicy"
}

private fun ByteArray.base64(): String = usePinned { pinned ->
    NSData.dataWithBytes(bytes = pinned.addressOf(0), length = size.toULong())
        .base64EncodedStringWithOptions(0u)
}

private fun transportRequiredFailure(): CompanionFailure = CompanionFailure(
    CompanionFailureCode.TRANSPORT_UNAVAILABLE,
    "Connect securely to the paired desktop before starting inspection.",
    recoverable = true,
)

private fun vpnConfigurationFailure(): CompanionFailure = CompanionFailure(
    CompanionFailureCode.VPN_START_FAILED,
    "The KNet inspection VPN configuration is unavailable.",
    recoverable = true,
)

private fun vpnPermissionFailure(): CompanionFailure = CompanionFailure(
    CompanionFailureCode.VPN_PERMISSION_DENIED,
    "Allow the KNet inspection VPN configuration to start inspection.",
    recoverable = true,
)

private fun vpnStartFailure(): CompanionFailure = CompanionFailure(
    CompanionFailureCode.VPN_START_FAILED,
    "The KNet inspection VPN could not be started.",
    recoverable = true,
)

private fun vpnStoppedUnexpectedlyFailure(): CompanionFailure = CompanionFailure(
    CompanionFailureCode.VPN_START_FAILED,
    "The KNet inspection VPN stopped unexpectedly. Start inspection again when the desktop is available.",
    recoverable = true,
)
