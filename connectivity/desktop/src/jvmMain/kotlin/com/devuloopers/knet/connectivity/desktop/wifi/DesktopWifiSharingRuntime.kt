package com.devuloopers.knet.connectivity.desktop.wifi

import com.devuloopers.knet.application.contract.connectivity.wifi.WifiSharing
import com.devuloopers.knet.application.contract.proxy.ProxyRuntime
import com.devuloopers.knet.application.contract.proxy.ProxyRuntimeState
import com.devuloopers.knet.connectivity.desktop.DesktopConnectivityRuntime
import com.devuloopers.knet.connectivity.desktop.network.availableLanAddresses
import com.devuloopers.knet.connectivity.model.ConnectivityContext
import com.devuloopers.knet.connectivity.model.ConnectivityMechanismId
import com.devuloopers.knet.connectivity.model.NetworkAddress
import com.devuloopers.knet.connectivity.model.ProxyAccessRequirement
import com.devuloopers.knet.connectivity.model.ProxyEndpoint
import com.devuloopers.knet.connectivity.model.ProxyEndpointScope
import com.devuloopers.knet.connectivity.model.WifiSharingMetrics
import com.devuloopers.knet.connectivity.model.WifiSharingFailure
import com.devuloopers.knet.connectivity.model.WifiSharingListenerEndpoint
import com.devuloopers.knet.connectivity.model.WifiSharingListenerFailureReason
import com.devuloopers.knet.connectivity.model.WifiSharingListenerKind
import com.devuloopers.knet.connectivity.model.WifiSharingSession
import com.devuloopers.knet.connectivity.model.WifiSharingSessionId
import com.devuloopers.knet.connectivity.model.WifiSharingState
import com.devuloopers.knet.traffic.model.IngressAttributionRegistration
import java.net.BindException
import java.net.InetSocketAddress
import java.net.SocketException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock as withResourceLock
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Automatically exposes a running loopback proxy on one exact desktop LAN address.
 *
 * Proxy state is the single lifecycle authority: starting the proxy creates the Wi-Fi gateway and setup
 * page, while stopping it closes both. Network changes select another viable IPv4 address additively without
 * introducing LAN listener behavior into the proxy engine.
 *
 * @param setupPortalPorts Ordered setup-page port candidates. The proxy endpoint port is excluded at runtime.
 * @param onActivationFailure Product-owned diagnostic callback retaining the typed reason and platform cause.
 * @param onRecovery Product-owned diagnostic callback invoked after automatic listener recovery.
 */
@OptIn(ExperimentalUuidApi::class)
public class DesktopWifiSharingRuntime(
    private val proxyRuntime: ProxyRuntime,
    private val connectivityRuntime: DesktopConnectivityRuntime,
    private val attributions: IngressAttributionRegistration,
    private val certificateDer: () -> ByteArray,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    setupPortalPorts: List<Int> = DEFAULT_SETUP_PORTS,
    private val onActivationFailure: (WifiSharingFailure, Throwable) -> Unit = { _, _ -> },
    private val onRecovery: (ProxyEndpoint) -> Unit = {},
) : WifiSharing, AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val lifecycleMutex = Mutex()
    private val resourceLock = ReentrantLock()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val setupPortalPorts = setupPortalPorts.toList()
    private val mutableState = MutableStateFlow<WifiSharingState>(
        WifiSharingState.Disabled(availableAddresses()),
    )
    override val state: StateFlow<WifiSharingState> = mutableState.asStateFlow()

    @Volatile
    private var activeSession: WifiSharingSession? = null

    @Volatile
    private var gateway: WifiLanProxyGateway? = null

    @Volatile
    private var setupPortal: WifiSetupPortal? = null

    /** Protected by [lifecycleMutex]; cancellation invalidates a superseded retry sequence. */
    private var recoveryJob: Job? = null

    /** Protected by [lifecycleMutex]; prevents an old retry from publishing after context replacement. */
    private var recoveryGeneration: Long = 0L

    init {
        require(this.setupPortalPorts.size >= MINIMUM_SETUP_PORT_CANDIDATES) {
            "Wi-Fi sharing requires at least two setup portal port candidates."
        }
        require(this.setupPortalPorts.distinct().size == this.setupPortalPorts.size) {
            "Wi-Fi setup portal port candidates must be unique."
        }
        require(this.setupPortalPorts.all { it in 1..65_535 }) {
            "Wi-Fi setup portal ports must be between 1 and 65535."
        }
    }

    init {
        scope.launch {
            connectivityRuntime.context.collect { context ->
                lifecycleMutex.withLock {
                    cancelRecoveryLocked()
                    when (val outcome = reconcileLocked(context, publishEnabling = true)) {
                        ReconcileOutcome.Settled -> Unit
                        is ReconcileOutcome.NeedsRecovery -> scheduleRecoveryLocked(outcome.failure)
                    }
                }
            }
        }
    }

    private fun reconcileLocked(
        context: ConnectivityContext,
        publishEnabling: Boolean,
    ): ReconcileOutcome {
        if (closed.get()) return ReconcileOutcome.Settled
        val addresses = context.network.addresses.availableLanAddresses()
        val internalEndpoint = context.proxyEndpoints.endpoints
            .firstOrNull { it.scope == ProxyEndpointScope.LOOPBACK }
        if (internalEndpoint == null) {
            if (activeSession != null) {
                mutableState.value = WifiSharingState.Disabling
                closeActiveResourcesLocked()
            }
            mutableState.value = WifiSharingState.Disabled(addresses)
            return ReconcileOutcome.Settled
        }

        val current = activeSession
        val selectedAddress = current?.networkAddress?.takeIf { it in addresses } ?: addresses.preferredAddress()
        if (selectedAddress == null) {
            closeActiveResourcesLocked()
            mutableState.value = WifiSharingState.Failed(
                failure = WifiSharingFailure.NetworkAddressUnavailable,
                recoverable = true,
                availableAddresses = addresses,
            )
            return ReconcileOutcome.Settled
        }

        if (
            current != null &&
            current.networkAddress == selectedAddress &&
            current.proxyEndpoint.port == internalEndpoint.port
        ) {
            return ReconcileOutcome.Settled
        }

        closeActiveResourcesLocked()
        return activateLocked(
            selectedAddress = selectedAddress,
            proxyPort = internalEndpoint.port,
            networkVersion = context.network.version,
            publishEnabling = publishEnabling,
        )
    }

    private fun activateLocked(
        selectedAddress: NetworkAddress,
        proxyPort: Int,
        networkVersion: Long,
        publishEnabling: Boolean,
    ): ReconcileOutcome {
        if (publishEnabling) mutableState.value = WifiSharingState.Enabling
        val certificate = runCatching(certificateDer).getOrElse {
            return ReconcileOutcome.NeedsRecovery(
                ActivationFailure(
                    failure = WifiSharingFailure.CertificateUnavailable,
                    cause = it,
                    fastRetry = false,
                ),
            )
        }
        if (certificate.isEmpty()) {
            return ReconcileOutcome.NeedsRecovery(
                ActivationFailure(
                    failure = WifiSharingFailure.CertificateUnavailable,
                    cause = IllegalStateException("KNet root certificate is empty."),
                    fastRetry = false,
                ),
            )
        }
        val fingerprint = certificate.sha256()
        val endpoint = ProxyEndpoint(
            host = selectedAddress.address,
            port = proxyPort,
            scope = ProxyEndpointScope.LAN,
            accessRequirement = ProxyAccessRequirement.OPEN_LAN_CLIENT,
        )
        val createdGateway = WifiLanProxyGateway(
            bindHost = selectedAddress.address,
            bindPort = proxyPort,
            targetProxy = ::currentLoopbackProxy,
            attributions = attributions,
            nowMillis = nowMillis,
            onMetricsChanged = { scope.launch { publishMetrics() } },
        )
        try {
            createdGateway.start()
        } catch (failure: Exception) {
            runCatching(createdGateway::close)
            return ReconcileOutcome.NeedsRecovery(
                failure.toActivationFailure(
                    listener = WifiSharingListenerKind.LAN_PROXY_GATEWAY,
                    host = selectedAddress.address,
                    port = proxyPort,
                ),
            )
        }

        val startedPortal = startSetupPortal(
            bindHost = selectedAddress.address,
            proxyPort = proxyPort,
            endpoint = endpoint,
            certificate = certificate,
            certificateSha256 = fingerprint,
        )
        if (startedPortal is PortalStartOutcome.Failed) {
            runCatching(createdGateway::close)
            return ReconcileOutcome.NeedsRecovery(startedPortal.failure)
        }
        startedPortal as PortalStartOutcome.Started
        val session = WifiSharingSession(
            id = WifiSharingSessionId(Uuid.random().toString()),
            networkAddress = selectedAddress,
            proxyEndpoint = endpoint,
            setupUrl = setupUrl(selectedAddress.address, startedPortal.port),
            certificateSha256 = fingerprint,
            networkVersion = networkVersion,
            startedAtEpochMillis = nowMillis(),
        )

        try {
            resourceLock.withResourceLock {
                check(!closed.get()) { RUNTIME_CLOSED }
                activeSession = session
                gateway = createdGateway
                setupPortal = startedPortal.portal
                connectivityRuntime.publishEndpoint(MECHANISM_ID, endpoint)
                mutableState.value = WifiSharingState.Active(session, createdGateway.metrics())
            }
            return ReconcileOutcome.Settled
        } catch (failure: Exception) {
            resourceLock.withResourceLock {
                if (activeSession == session) activeSession = null
                if (gateway === createdGateway) gateway = null
                if (setupPortal === startedPortal.portal) setupPortal = null
                connectivityRuntime.publishEndpoint(MECHANISM_ID, null)
            }
            runCatching(startedPortal.portal::close)
            runCatching(createdGateway::close)
            if (closed.get()) {
                mutableState.value = WifiSharingState.Disabled(availableAddresses())
                return ReconcileOutcome.Settled
            } else {
                return ReconcileOutcome.NeedsRecovery(
                    ActivationFailure(
                        failure = WifiSharingFailure.Unexpected,
                        cause = failure,
                        fastRetry = false,
                    ),
                )
            }
        }
    }

    /**
     * Tries the preferred setup port followed by an isolated fallback. A setup-port conflict never changes
     * the stable phone proxy endpoint; only the QR/setup URL reflects the selected portal port.
     */
    private fun startSetupPortal(
        bindHost: String,
        proxyPort: Int,
        endpoint: ProxyEndpoint,
        certificate: ByteArray,
        certificateSha256: String,
    ): PortalStartOutcome {
        var latestFailure: ActivationFailure? = null
        setupPortsFor(proxyPort).forEachIndexed { index, setupPort ->
            val candidate = WifiSetupPortal(
                bindHost = bindHost,
                bindPort = setupPort,
                proxyEndpoint = endpoint,
                certificateDer = { certificate.copyOf() },
                certificateSha256 = certificateSha256,
            )
            try {
                candidate.start()
                if (index > 0) {
                    latestFailure?.let { failure ->
                        reportActivationFailure(failure)
                    }
                }
                return PortalStartOutcome.Started(candidate, setupPort)
            } catch (failure: Exception) {
                runCatching(candidate::close)
                latestFailure = failure.toActivationFailure(
                    listener = WifiSharingListenerKind.SETUP_PORTAL,
                    host = bindHost,
                    port = setupPort,
                )
                val listenerFailure = latestFailure.failure as WifiSharingFailure.ListenerUnavailable
                if (listenerFailure.reason != WifiSharingListenerFailureReason.ADDRESS_IN_USE) {
                    return PortalStartOutcome.Failed(latestFailure)
                }
            }
        }
        return PortalStartOutcome.Failed(checkNotNull(latestFailure))
    }

    /** Starts one generation-owned retry loop; context replacement cancels it before scheduling another. */
    private fun scheduleRecoveryLocked(initialFailure: ActivationFailure) {
        val generation = ++recoveryGeneration
        reportActivationFailure(initialFailure)
        if (initialFailure.fastRetry) {
            mutableState.value = initialFailure.recoveringState(attempt = 1, delayMillis = FAST_RETRY_DELAYS_MILLIS.first())
        } else {
            mutableState.value = initialFailure.failedState()
        }
        recoveryJob = scope.launch {
            recover(generation, initialFailure)
        }
    }

    /**
     * Retries outside the lifecycle mutex, acquiring it only for one transactional activation attempt.
     * Generation checks prevent a stale retry from publishing after proxy, network, or shutdown changes.
     */
    private suspend fun recover(
        generation: Long,
        initialFailure: ActivationFailure,
    ) {
        var latestFailure = initialFailure
        if (latestFailure.fastRetry) {
            for ((index, retryDelayMillis) in FAST_RETRY_DELAYS_MILLIS.withIndex()) {
                delay(retryDelayMillis)
                val outcome = retryOnce(generation) ?: return
                if (outcome is ReconcileOutcome.Settled) {
                    notifyRecoveryIfActive()
                    return
                }
                latestFailure = (outcome as ReconcileOutcome.NeedsRecovery).failure
                val nextDelay = FAST_RETRY_DELAYS_MILLIS.getOrNull(index + 1)
                if (!latestFailure.fastRetry || nextDelay == null) break
                lifecycleMutex.withLock {
                    if (generation == recoveryGeneration && !closed.get()) {
                        mutableState.value = latestFailure.recoveringState(index + 2, nextDelay)
                    }
                }
            }
        }

        lifecycleMutex.withLock {
            if (generation != recoveryGeneration || closed.get()) return
            mutableState.value = latestFailure.failedState()
        }

        while (currentCoroutineContext().isActive && !closed.get()) {
            delay(SLOW_RETRY_MILLIS)
            val outcome = retryOnce(generation) ?: return
            if (outcome is ReconcileOutcome.Settled) {
                notifyRecoveryIfActive()
                return
            }
            latestFailure = (outcome as ReconcileOutcome.NeedsRecovery).failure
            lifecycleMutex.withLock {
                if (generation != recoveryGeneration || closed.get()) return
                mutableState.value = latestFailure.failedState()
            }
        }
    }

    private suspend fun retryOnce(generation: Long): ReconcileOutcome? = lifecycleMutex.withLock {
        if (generation != recoveryGeneration || closed.get()) return@withLock null
        reconcileLocked(connectivityRuntime.context.value, publishEnabling = false)
    }

    private fun cancelRecoveryLocked() {
        recoveryGeneration += 1L
        recoveryJob?.cancel()
        recoveryJob = null
    }

    private fun ActivationFailure.recoveringState(attempt: Int, delayMillis: Long): WifiSharingState.Recovering =
        WifiSharingState.Recovering(
            failure = failure,
            attempt = attempt,
            retryInMillis = delayMillis,
            availableAddresses = availableAddresses(),
        )

    private fun ActivationFailure.failedState(): WifiSharingState.Failed = WifiSharingState.Failed(
        failure = failure,
        recoverable = true,
        availableAddresses = availableAddresses(),
    )

    private fun Throwable.toActivationFailure(
        listener: WifiSharingListenerKind,
        host: String,
        port: Int,
    ): ActivationFailure = ActivationFailure(
        failure = WifiSharingFailure.ListenerUnavailable(
            listener = listener,
            endpoint = WifiSharingListenerEndpoint(host, port),
            reason = listenerFailureReason(),
        ),
        cause = this,
        fastRetry = true,
    )

    private fun Throwable.listenerFailureReason(): WifiSharingListenerFailureReason {
        val causes = generateSequence(this) { current -> current.cause }.toList()
        val normalizedMessages = causes.mapNotNull(Throwable::message).joinToString(" ").lowercase()
        return when {
            "permission denied" in normalizedMessages -> WifiSharingListenerFailureReason.PERMISSION_DENIED
            "cannot assign requested address" in normalizedMessages ||
                "can't assign requested address" in normalizedMessages ->
                WifiSharingListenerFailureReason.ADDRESS_UNAVAILABLE
            causes.any { failure -> failure is BindException } && "in use" in normalizedMessages ->
                WifiSharingListenerFailureReason.ADDRESS_IN_USE
            causes.any { failure -> failure is BindException || failure is SocketException } ->
                WifiSharingListenerFailureReason.UNKNOWN
            else -> WifiSharingListenerFailureReason.UNKNOWN
        }
    }

    private fun reportActivationFailure(failure: ActivationFailure) {
        runCatching { onActivationFailure(failure.failure, failure.cause) }
    }

    private fun notifyRecoveryIfActive() {
        val session = activeSession ?: return
        runCatching { onRecovery(session.proxyEndpoint) }
    }

    private fun currentLoopbackProxy(): InetSocketAddress? =
        (proxyRuntime.state.value as? ProxyRuntimeState.Running)
            ?.handle
            ?.endpoints
            ?.endpoints
            ?.firstOrNull { it.scope == ProxyEndpointScope.LOOPBACK }
            ?.let { InetSocketAddress(it.host, it.port) }

    private suspend fun publishMetrics() {
        lifecycleMutex.withLock {
            val session = activeSession ?: return@withLock
            val metrics = gateway?.metrics() ?: WifiSharingMetrics()
            mutableState.value = WifiSharingState.Active(session, metrics)
        }
    }

    private fun closeActiveResourcesLocked() {
        resourceLock.withResourceLock {
            val portal = setupPortal.also { setupPortal = null }
            val activeGateway = gateway.also { gateway = null }
            activeSession = null
            connectivityRuntime.publishEndpoint(MECHANISM_ID, null)
            runCatching { portal?.close() }
            runCatching { activeGateway?.close() }
        }
    }

    private fun availableAddresses(): List<NetworkAddress> =
        connectivityRuntime.context.value.network.addresses.availableLanAddresses()

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        scope.cancel()
        resourceLock.withResourceLock {
            val portal = setupPortal.also { setupPortal = null }
            val activeGateway = gateway.also { gateway = null }
            activeSession = null
            connectivityRuntime.publishEndpoint(MECHANISM_ID, null)
            runCatching { portal?.close() }
            runCatching { activeGateway?.close() }
            mutableState.value = WifiSharingState.Disabled(availableAddresses())
        }
    }

    private fun List<NetworkAddress>.preferredAddress(): NetworkAddress? = firstOrNull()

    private fun setupPortsFor(proxyPort: Int): List<Int> =
        setupPortalPorts.filterNot { it == proxyPort }

    private fun setupUrl(host: String, port: Int): String =
        "http://${host.authorityHost()}:$port/setup"

    private fun String.authorityHost(): String = if (':' in this && !startsWith('[')) "[$this]" else this

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private data class ActivationFailure(
        val failure: WifiSharingFailure,
        val cause: Throwable,
        val fastRetry: Boolean,
    )

    private sealed interface ReconcileOutcome {
        data object Settled : ReconcileOutcome

        data class NeedsRecovery(val failure: ActivationFailure) : ReconcileOutcome
    }

    private sealed interface PortalStartOutcome {
        data class Started(val portal: WifiSetupPortal, val port: Int) : PortalStartOutcome

        data class Failed(val failure: ActivationFailure) : PortalStartOutcome
    }

    private companion object {
        val MECHANISM_ID: ConnectivityMechanismId = ConnectivityMechanismId("wifi-sharing")
        const val RUNTIME_CLOSED: String = "wifi_runtime_closed"
        const val MINIMUM_SETUP_PORT_CANDIDATES: Int = 2
        val DEFAULT_SETUP_PORTS: List<Int> = listOf(8_181, 8_183)
        val FAST_RETRY_DELAYS_MILLIS: List<Long> = listOf(100L, 250L, 500L, 1_000L, 2_000L)
        const val SLOW_RETRY_MILLIS: Long = 2_000L
    }
}
