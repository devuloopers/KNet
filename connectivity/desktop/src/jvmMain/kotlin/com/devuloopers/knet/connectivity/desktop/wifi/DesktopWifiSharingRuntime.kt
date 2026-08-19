package com.devuloopers.knet.connectivity.desktop.wifi

import com.devuloopers.knet.application.port.connectivity.wifi.WifiSharingPort
import com.devuloopers.knet.application.port.proxy.ProxyRuntimePort
import com.devuloopers.knet.application.port.proxy.ProxyRuntimeState
import com.devuloopers.knet.connectivity.desktop.DesktopConnectivityRuntime
import com.devuloopers.knet.connectivity.model.ConnectivityContext
import com.devuloopers.knet.connectivity.model.ConnectivityMechanismId
import com.devuloopers.knet.connectivity.model.NetworkAddress
import com.devuloopers.knet.connectivity.model.NetworkAddressFamily
import com.devuloopers.knet.connectivity.model.ProxyAccessRequirement
import com.devuloopers.knet.connectivity.model.ProxyEndpoint
import com.devuloopers.knet.connectivity.model.ProxyEndpointScope
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
    private var gateway: WifiLanProxyGateway? = null

    @Volatile
    private var setupPortal: WifiSetupPortal? = null

    init {
        scope.launch {
            connectivityRuntime.context.collect { context ->
                lifecycleMutex.withLock { reconcileLocked(context) }
            }
        }
        scope.launch {
            while (isActive && !closed.get()) {
                delay(AUTOMATIC_RETRY_MILLIS)
                lifecycleMutex.withLock { reconcileLocked(connectivityRuntime.context.value) }
            }
        }
    }

    private fun reconcileLocked(context: ConnectivityContext) {
        if (closed.get()) return
        val addresses = context.network.addresses.availableForSharing()
        val internalEndpoint = context.proxyEndpoints.endpoints
            .firstOrNull { it.scope == ProxyEndpointScope.LOOPBACK }
        if (internalEndpoint == null) {
            if (activeSession != null) {
                mutableState.value = WifiSharingState.Disabling
                closeActiveResourcesLocked()
            }
            mutableState.value = WifiSharingState.Disabled(addresses)
            return
        }

        val current = activeSession
        val selectedAddress = current?.networkAddress?.takeIf { it in addresses } ?: addresses.preferredAddress()
        if (selectedAddress == null) {
            closeActiveResourcesLocked()
            mutableState.value = WifiSharingState.Failed(
                code = ADDRESS_UNAVAILABLE,
                recoverable = true,
                availableAddresses = addresses,
            )
            return
        }

        val setupPort = setupPortFor(internalEndpoint.port)
        if (
            current != null &&
            current.networkAddress == selectedAddress &&
            current.proxyEndpoint.port == internalEndpoint.port &&
            current.setupUrl == setupUrl(selectedAddress.address, setupPort)
        ) {
            return
        }

        closeActiveResourcesLocked()
        activateLocked(
            selectedAddress = selectedAddress,
            proxyPort = internalEndpoint.port,
            setupPort = setupPort,
            networkVersion = context.network.version,
        )
    }

    private fun activateLocked(
        selectedAddress: NetworkAddress,
        proxyPort: Int,
        setupPort: Int,
        networkVersion: Long,
    ) {
        mutableState.value = WifiSharingState.Enabling
        val certificate = runCatching(certificateDer).getOrElse {
            failActivation(CERTIFICATE_UNAVAILABLE)
            return
        }
        if (certificate.isEmpty()) {
            failActivation(CERTIFICATE_UNAVAILABLE)
            return
        }
        val fingerprint = certificate.sha256()
        val endpoint = ProxyEndpoint(
            host = selectedAddress.address,
            port = proxyPort,
            scope = ProxyEndpointScope.LAN,
            accessRequirement = ProxyAccessRequirement.OPEN_LAN_CLIENT,
        )
        val session = WifiSharingSession(
            id = WifiSharingSessionId(Uuid.random().toString()),
            networkAddress = selectedAddress,
            proxyEndpoint = endpoint,
            setupUrl = setupUrl(selectedAddress.address, setupPort),
            certificateSha256 = fingerprint,
            networkVersion = networkVersion,
            startedAtEpochMillis = nowMillis(),
        )
        val createdGateway = WifiLanProxyGateway(
            bindHost = selectedAddress.address,
            bindPort = proxyPort,
            targetProxy = ::currentLoopbackProxy,
            attributions = attributions,
            nowMillis = nowMillis,
            onMetricsChanged = { scope.launch { publishMetrics() } },
        )
        val createdPortal = WifiSetupPortal(
            bindHost = selectedAddress.address,
            bindPort = setupPort,
            proxyEndpoint = endpoint,
            certificateDer = { certificate.copyOf() },
            certificateSha256 = fingerprint,
        )

        try {
            createdGateway.start()
            createdPortal.start()
            resourceLock.withResourceLock {
                check(!closed.get()) { RUNTIME_CLOSED }
                activeSession = session
                gateway = createdGateway
                setupPortal = createdPortal
                connectivityRuntime.publishEndpoint(MECHANISM_ID, endpoint)
                mutableState.value = WifiSharingState.Active(session, createdGateway.metrics())
            }
        } catch (_: Exception) {
            runCatching(createdPortal::close)
            runCatching(createdGateway::close)
            if (closed.get()) {
                mutableState.value = WifiSharingState.Disabled(availableAddresses())
            } else {
                failActivation(BIND_FAILED)
            }
        }
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

    private fun failActivation(code: String) {
        mutableState.value = WifiSharingState.Failed(
            code = code,
            recoverable = true,
            availableAddresses = availableAddresses(),
        )
    }

    private fun availableAddresses(): List<NetworkAddress> =
        connectivityRuntime.context.value.network.addresses.availableForSharing()

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

    private fun List<NetworkAddress>.availableForSharing(): List<NetworkAddress> =
        filter { address -> !address.loopback && address.family == NetworkAddressFamily.IPV4 }
            .distinctBy { address -> address.interfaceId to address.address }
            .sortedWith(
                compareBy<NetworkAddress> { address -> address.interfaceId.virtualInterfacePriority() }
                    .thenBy { address -> address.address.privateAddressPriority() }
                    .thenBy(NetworkAddress::interfaceId)
                    .thenBy(NetworkAddress::address),
            )

    private fun List<NetworkAddress>.preferredAddress(): NetworkAddress? = firstOrNull()

    private fun String.virtualInterfacePriority(): Int {
        val normalized = lowercase()
        return if (VIRTUAL_INTERFACE_PREFIXES.any(normalized::startsWith)) 1 else 0
    }

    private fun String.privateAddressPriority(): Int {
        val octets = split('.').mapNotNull(String::toIntOrNull)
        if (octets.size != 4) return 1
        val local = octets[0] == 10 ||
            (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168) ||
            (octets[0] == 169 && octets[1] == 254)
        return if (local) 0 else 1
    }

    private fun setupPortFor(proxyPort: Int): Int =
        if (proxyPort == DEFAULT_SETUP_PORT) ALTERNATE_SETUP_PORT else DEFAULT_SETUP_PORT

    private fun setupUrl(host: String, port: Int): String =
        "http://${host.authorityHost()}:$port/setup"

    private fun String.authorityHost(): String = if (':' in this && !startsWith('[')) "[$this]" else this

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private companion object {
        val MECHANISM_ID: ConnectivityMechanismId = ConnectivityMechanismId("wifi-sharing")
        val VIRTUAL_INTERFACE_PREFIXES: Set<String> = setOf(
            "utun",
            "tun",
            "tap",
            "docker",
            "veth",
            "bridge",
            "awdl",
            "llw",
        )
        const val RUNTIME_CLOSED: String = "wifi_runtime_closed"
        const val ADDRESS_UNAVAILABLE: String = "wifi_address_unavailable"
        const val CERTIFICATE_UNAVAILABLE: String = "certificate_unavailable"
        const val BIND_FAILED: String = "wifi_bind_failed"
        const val DEFAULT_SETUP_PORT: Int = 8_181
        const val ALTERNATE_SETUP_PORT: Int = 8_183
        const val AUTOMATIC_RETRY_MILLIS: Long = 2_000L
    }
}
