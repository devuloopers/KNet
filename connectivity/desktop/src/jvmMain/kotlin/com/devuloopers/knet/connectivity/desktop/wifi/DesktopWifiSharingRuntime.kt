package com.devuloopers.knet.connectivity.desktop.wifi

import com.devuloopers.knet.application.port.connectivity.wifi.WifiClientApprovalResult
import com.devuloopers.knet.application.port.connectivity.wifi.WifiInvitationResult
import com.devuloopers.knet.application.port.connectivity.wifi.WifiSharingOperationResult
import com.devuloopers.knet.application.port.connectivity.wifi.WifiSharingPort
import com.devuloopers.knet.application.port.connectivity.wifi.WifiSharingStopReason
import com.devuloopers.knet.application.port.proxy.ProxyRuntimePort
import com.devuloopers.knet.application.port.proxy.ProxyRuntimeState
import com.devuloopers.knet.connectivity.desktop.DesktopConnectivityRuntime
import com.devuloopers.knet.connectivity.model.ConnectivityMechanismId
import com.devuloopers.knet.connectivity.model.NetworkAddress
import com.devuloopers.knet.connectivity.model.ProxyAccessRequirement
import com.devuloopers.knet.connectivity.model.ProxyEndpoint
import com.devuloopers.knet.connectivity.model.ProxyEndpointScope
import com.devuloopers.knet.connectivity.model.WifiClientCandidateId
import com.devuloopers.knet.connectivity.model.WifiClientId
import com.devuloopers.knet.connectivity.model.WifiSharingActionReason
import com.devuloopers.knet.connectivity.model.WifiSharingConfiguration
import com.devuloopers.knet.connectivity.model.WifiSharingMetrics
import com.devuloopers.knet.connectivity.model.WifiSharingSession
import com.devuloopers.knet.connectivity.model.WifiSharingSessionId
import com.devuloopers.knet.connectivity.model.WifiSharingState
import com.devuloopers.knet.traffic.model.IngressAttributionRegistration
import java.net.InetSocketAddress
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Desktop implementation of the application Wi-Fi sharing boundary.
 *
 * The runtime owns exact-interface listeners, invitations, and source approvals. It publishes one LAN
 * endpoint into desktop connectivity state while forwarding every admitted stream to the unchanged
 * loopback proxy.
 */
@OptIn(ExperimentalUuidApi::class)
public class DesktopWifiSharingRuntime(
    private val proxyRuntime: ProxyRuntimePort,
    private val connectivityRuntime: DesktopConnectivityRuntime,
    private val attributions: IngressAttributionRegistration,
    private val certificateDer: () -> ByteArray,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : WifiSharingPort, AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val lifecycleMutex = Mutex()
    private val resourceLock = ReentrantLock()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutableState = MutableStateFlow<WifiSharingState>(
        WifiSharingState.Disabled(availableAddresses()),
    )
    override val state: StateFlow<WifiSharingState> = mutableState.asStateFlow()

    @Volatile
    private var activeSession: WifiSharingSession? = null
    @Volatile
    private var invitationService: WifiInvitationService? = null
    @Volatile
    private var approvalRegistry: WifiClientApprovalRegistry? = null
    @Volatile
    private var gateway: WifiLanProxyGateway? = null
    @Volatile
    private var setupPortal: WifiSetupPortal? = null

    init {
        scope.launch {
            connectivityRuntime.context.collect { context ->
                lifecycleMutex.withLock {
                    reconcileNetworkLocked(
                        networkVersion = context.network.version,
                        addresses = context.network.addresses,
                        proxyRunning = context.proxyEndpoints.endpoints.any {
                            it.scope == ProxyEndpointScope.LOOPBACK
                        },
                    )
                }
            }
        }
        scope.launch {
            while (!closed.get()) {
                delay(APPROVAL_EXPIRY_REFRESH_MILLIS)
                lifecycleMutex.withLock {
                    val approvals = approvalRegistry ?: return@withLock
                    publishActiveStateLocked(approvals.snapshot())
                }
            }
        }
    }

    override suspend fun enable(configuration: WifiSharingConfiguration): WifiSharingOperationResult =
        lifecycleMutex.withLock {
            if (closed.get()) return@withLock WifiSharingOperationResult.Rejected(RUNTIME_CLOSED)
            val currentSession = activeSession
            if (currentSession != null) {
                return@withLock if (
                    currentSession.networkAddress == configuration.networkAddress &&
                    currentSession.proxyEndpoint.port == configuration.proxyPort
                ) {
                    WifiSharingOperationResult.Succeeded
                } else {
                    WifiSharingOperationResult.Rejected(ALREADY_ACTIVE)
                }
            }

            mutableState.value = WifiSharingState.Enabling
            val context = connectivityRuntime.context.value
            val selectedAddress = context.network.addresses.singleOrNull { it == configuration.networkAddress }
                ?: return@withLock failActivation(ADDRESS_UNAVAILABLE)
            if (selectedAddress.loopback) return@withLock failActivation(ADDRESS_UNAVAILABLE)
            val internalEndpoint = (proxyRuntime.state.value as? ProxyRuntimeState.Running)
                ?.handle?.endpoints?.endpoints
                ?.singleOrNull { it.scope == ProxyEndpointScope.LOOPBACK }
                ?: return@withLock failActivation(PROXY_NOT_RUNNING)

            val certificate = runCatching(certificateDer).getOrElse {
                return@withLock failActivation(CERTIFICATE_UNAVAILABLE)
            }
            if (certificate.isEmpty()) return@withLock failActivation(CERTIFICATE_UNAVAILABLE)

            val endpoint = ProxyEndpoint(
                host = selectedAddress.address,
                port = configuration.proxyPort,
                scope = ProxyEndpointScope.LAN,
                accessRequirement = ProxyAccessRequirement.APPROVED_LAN_CLIENT,
            )
            val setupBaseUrl = "http://${selectedAddress.address.authorityHost()}:${configuration.setupPort}"
            val session = WifiSharingSession(
                id = WifiSharingSessionId(Uuid.random().toString()),
                networkAddress = selectedAddress,
                proxyEndpoint = endpoint,
                setupBaseUrl = setupBaseUrl,
                certificateSha256 = certificate.sha256(),
                networkVersion = context.network.version,
                startedAtEpochMillis = nowMillis(),
            )
            val approvals = WifiClientApprovalRegistry(
                nowMillis = nowMillis,
                onChanged = { snapshot ->
                    scope.launch { publishActiveSnapshot(snapshot) }
                },
            )
            val invitations = WifiInvitationService(setupBaseUrl, nowMillis)
            val createdGateway = WifiLanProxyGateway(
                bindHost = selectedAddress.address,
                bindPort = configuration.proxyPort,
                targetProxy = { InetSocketAddress(internalEndpoint.host, internalEndpoint.port) },
                approvals = approvals,
                attributions = attributions,
                nowMillis = nowMillis,
                onMetricsChanged = {
                    scope.launch { publishActiveSnapshot() }
                },
            )
            val portal = WifiSetupPortal(
                bindHost = selectedAddress.address,
                bindPort = configuration.setupPort,
                proxyEndpoint = endpoint,
                certificateDer = { certificate.copyOf() },
                invitations = invitations,
                approvals = approvals,
            )

            try {
                createdGateway.start()
                portal.start()
                val committed = resourceLock.withResourceLock {
                    if (closed.get()) {
                        false
                    } else {
                        activeSession = session
                        approvalRegistry = approvals
                        invitationService = invitations
                        gateway = createdGateway
                        setupPortal = portal
                        connectivityRuntime.publishEndpoint(MECHANISM_ID, endpoint)
                        publishActiveStateLocked(approvals.snapshot())
                        true
                    }
                }
                if (!committed) error(RUNTIME_CLOSED)
                WifiSharingOperationResult.Succeeded
            } catch (_: Exception) {
                runCatching(portal::close)
                runCatching(createdGateway::close)
                approvals.clear()
                invitations.invalidateAll()
                if (closed.get()) {
                    mutableState.value = WifiSharingState.Disabled(availableAddresses())
                    WifiSharingOperationResult.Rejected(RUNTIME_CLOSED)
                } else {
                    failActivation(BIND_FAILED)
                }
            }
        }

    override suspend fun disable(reason: WifiSharingStopReason): WifiSharingOperationResult =
        lifecycleMutex.withLock {
            if (closed.get()) return@withLock WifiSharingOperationResult.Rejected(RUNTIME_CLOSED)
            if (activeSession == null) {
                mutableState.value = WifiSharingState.Disabled(availableAddresses())
                return@withLock WifiSharingOperationResult.Succeeded
            }
            mutableState.value = WifiSharingState.Disabling
            closeActiveResourcesLocked()
            mutableState.value = WifiSharingState.Disabled(availableAddresses())
            WifiSharingOperationResult.Succeeded
        }

    override suspend fun createInvitation(): WifiInvitationResult = lifecycleMutex.withLock {
        if (closed.get()) return@withLock WifiInvitationResult.Rejected(RUNTIME_CLOSED)
        val invitations = invitationService ?: return@withLock WifiInvitationResult.Rejected(NOT_ACTIVE)
        WifiInvitationResult.Created(invitations.create())
    }

    override suspend fun approve(
        candidateId: WifiClientCandidateId,
        displayName: String,
    ): WifiClientApprovalResult = lifecycleMutex.withLock {
        if (closed.get()) return@withLock WifiClientApprovalResult.Rejected(RUNTIME_CLOSED)
        val approvals = approvalRegistry ?: return@withLock WifiClientApprovalResult.Rejected(NOT_ACTIVE)
        val approved = approvals.approve(candidateId, displayName)
            ?: return@withLock WifiClientApprovalResult.Rejected(CANDIDATE_UNAVAILABLE)
        publishActiveStateLocked(approvals.snapshot())
        WifiClientApprovalResult.Approved(approved)
    }

    override suspend fun reject(candidateId: WifiClientCandidateId): WifiSharingOperationResult =
        lifecycleMutex.withLock {
            val approvals = approvalRegistry ?: return@withLock WifiSharingOperationResult.Rejected(NOT_ACTIVE)
            if (!approvals.reject(candidateId)) {
                return@withLock WifiSharingOperationResult.Rejected(CANDIDATE_UNAVAILABLE)
            }
            publishActiveStateLocked(approvals.snapshot())
            WifiSharingOperationResult.Succeeded
        }

    override suspend fun revoke(clientId: WifiClientId): WifiSharingOperationResult = lifecycleMutex.withLock {
        val approvals = approvalRegistry ?: return@withLock WifiSharingOperationResult.Rejected(NOT_ACTIVE)
        val revoked = approvals.revoke(clientId)
            ?: return@withLock WifiSharingOperationResult.Rejected(CLIENT_UNAVAILABLE)
        gateway?.revoke(revoked.id)
        publishActiveStateLocked(approvals.snapshot())
        WifiSharingOperationResult.Succeeded
    }

    private suspend fun publishActiveSnapshot(snapshot: WifiClientApprovalSnapshot? = null) {
        lifecycleMutex.withLock {
            if (activeSession != null) {
                publishActiveStateLocked(snapshot ?: approvalRegistry?.snapshot() ?: return@withLock)
            }
        }
    }

    private fun publishActiveStateLocked(snapshot: WifiClientApprovalSnapshot) {
        val session = activeSession ?: return
        mutableState.value = WifiSharingState.Active(
            session = session,
            pendingClients = snapshot.pendingClients,
            approvedClients = snapshot.approvedClients,
            metrics = gateway?.metrics() ?: WifiSharingMetrics(),
        )
    }

    private fun reconcileNetworkLocked(
        networkVersion: Long,
        addresses: List<NetworkAddress>,
        proxyRunning: Boolean,
    ) {
        val session = activeSession
        if (session == null) {
            if (mutableState.value is WifiSharingState.Disabled) {
                mutableState.value = WifiSharingState.Disabled(addresses.availableForSharing())
            }
            return
        }
        val reason = when {
            !proxyRunning -> WifiSharingActionReason.PROXY_STOPPED
            session.networkAddress !in addresses -> WifiSharingActionReason.ADDRESS_REMOVED
            session.networkVersion != networkVersion -> WifiSharingActionReason.NETWORK_CHANGED
            else -> return
        }
        closeActiveResourcesLocked()
        mutableState.value = WifiSharingState.NeedsUserAction(reason, addresses.availableForSharing())
    }

    private fun closeActiveResourcesLocked() {
        resourceLock.withResourceLock {
            closeActiveResourcesUnsafe()
        }
    }

    private fun closeActiveResourcesUnsafe() {
        val portal = setupPortal.also { setupPortal = null }
        val activeGateway = gateway.also { gateway = null }
        val invitations = invitationService.also { invitationService = null }
        val approvals = approvalRegistry.also { approvalRegistry = null }
        activeSession = null
        connectivityRuntime.publishEndpoint(MECHANISM_ID, null)
        runCatching { portal?.close() }
        runCatching { activeGateway?.close() }
        invitations?.invalidateAll()
        approvals?.clear()
    }

    private fun failActivation(code: String): WifiSharingOperationResult.Rejected {
        mutableState.value = WifiSharingState.Failed(
            code = code,
            recoverable = true,
            availableAddresses = availableAddresses(),
        )
        return WifiSharingOperationResult.Rejected(code)
    }

    private fun availableAddresses(): List<NetworkAddress> =
        connectivityRuntime.context.value.network.addresses.availableForSharing()

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        scope.cancel()
        resourceLock.withResourceLock {
            closeActiveResourcesUnsafe()
            mutableState.value = WifiSharingState.Disabled(availableAddresses())
        }
    }

    private fun List<NetworkAddress>.availableForSharing(): List<NetworkAddress> =
        filterNot(NetworkAddress::loopback)
            .sortedWith(compareBy(NetworkAddress::interfaceId, NetworkAddress::family, NetworkAddress::address))

    private fun String.authorityHost(): String = if (':' in this && !startsWith('[')) "[$this]" else this

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private companion object {
        val MECHANISM_ID: ConnectivityMechanismId = ConnectivityMechanismId("wifi-sharing")
        const val RUNTIME_CLOSED: String = "wifi_runtime_closed"
        const val ALREADY_ACTIVE: String = "wifi_already_active"
        const val NOT_ACTIVE: String = "wifi_not_active"
        const val PROXY_NOT_RUNNING: String = "proxy_not_running"
        const val ADDRESS_UNAVAILABLE: String = "wifi_address_unavailable"
        const val CERTIFICATE_UNAVAILABLE: String = "certificate_unavailable"
        const val BIND_FAILED: String = "wifi_bind_failed"
        const val CANDIDATE_UNAVAILABLE: String = "wifi_candidate_unavailable"
        const val CLIENT_UNAVAILABLE: String = "wifi_client_unavailable"
        const val APPROVAL_EXPIRY_REFRESH_MILLIS: Long = 1_000L
    }
}
