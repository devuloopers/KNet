package com.devuloopers.knet.connectivity.desktop.wifi

import com.devuloopers.knet.application.port.proxy.ProxyRuntimeConfiguration
import com.devuloopers.knet.application.port.proxy.ProxyRuntimeHandle
import com.devuloopers.knet.application.port.proxy.ProxyRuntimePort
import com.devuloopers.knet.application.port.proxy.ProxyRuntimeState
import com.devuloopers.knet.application.port.proxy.ProxyStartResult
import com.devuloopers.knet.application.port.proxy.ProxyStopReason
import com.devuloopers.knet.application.port.proxy.ProxyStopResult
import com.devuloopers.knet.connectivity.desktop.DesktopConnectivityRuntime
import com.devuloopers.knet.connectivity.desktop.gateway.IngressAttributionRegistry
import com.devuloopers.knet.connectivity.desktop.network.DesktopNetworkObservation
import com.devuloopers.knet.connectivity.desktop.network.DesktopNetworkScanner
import com.devuloopers.knet.connectivity.desktop.network.DesktopNetworkSnapshotMonitor
import com.devuloopers.knet.connectivity.model.NetworkAddress
import com.devuloopers.knet.connectivity.model.NetworkAddressFamily
import com.devuloopers.knet.connectivity.model.ProxyAccessRequirement
import com.devuloopers.knet.connectivity.model.ProxyEndpoint
import com.devuloopers.knet.connectivity.model.ProxyEndpointScope
import com.devuloopers.knet.connectivity.model.ProxyEndpointSnapshot
import com.devuloopers.knet.connectivity.model.ProxyEndpointVersion
import com.devuloopers.knet.connectivity.model.WifiSharingState
import com.devuloopers.knet.traffic.model.IngressKind
import com.devuloopers.knet.traffic.model.TrafficEndpoint
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WifiSharingBackendTest {
    @Test
    fun `resource-backed setup page offers Android and Apple certificate downloads`() {
        val page = WifiSetupPageRenderer.render(
            WifiSetupPageModel("192.0.2.10", 8_080, "a".repeat(64)),
        )

        assertContains(page, "192.0.2.10:8080")
        assertContains(page, "/knet-ca.crt")
        assertContains(page, "/knet-ca.mobileconfig")
        assertContains(page, "Android")
        assertContains(page, "iPhone and iPad")

        val profile = AppleRootCertificateProfileRenderer.render(byteArrayOf(1, 2, 3))
        assertContains(profile, "com.apple.security.root")
        assertContains(profile, "AQID")
    }

    @Test
    fun `open setup portal serves page and both certificate formats without invitation`() {
        val address = localLanAddress()
        val setupPort = freePort(address.address)
        val endpoint = ProxyEndpoint(
            address.address,
            freePort(address.address),
            ProxyEndpointScope.LAN,
            ProxyAccessRequirement.OPEN_LAN_CLIENT,
        )
        val portal = WifiSetupPortal(
            bindHost = address.address,
            bindPort = setupPort,
            proxyEndpoint = endpoint,
            certificateDer = { byteArrayOf(1, 2, 3) },
            certificateSha256 = "a".repeat(64),
        )
        try {
            portal.start()

            val page = rawGet(address.address, setupPort, "/setup", address.address)
            assertContains(page, "200 OK")
            assertContains(page, "Wi-Fi Proxy Setup")
            assertContains(rawGet(address.address, setupPort, "/knet-ca.crt", address.address), "knet-ca.crt")
            assertContains(
                rawGet(address.address, setupPort, "/knet-ca.mobileconfig", address.address),
                "application/x-apple-aspen-config",
            )
            assertContains(rawGet(address.address, setupPort, "/proxy.pac", address.address), "PROXY")
            assertContains(rawGet(address.address, setupPort, "/setup", "untrusted.example"), "421")
        } finally {
            portal.close()
        }
    }

    @Test
    fun `open LAN stream is attributed by source address`() = runBlocking {
        val address = localLanAddress()
        val sourceAddress = address.address
        val internalProxy = ServerSocket(0, 16, java.net.InetAddress.getLoopbackAddress())
        val gatewayPort = freePort(sourceAddress)
        val attributions = IngressAttributionRegistry()
        val observed = CompletableFuture<com.devuloopers.knet.traffic.model.IngressContext?>()
        val internalWorker = Thread {
            internalProxy.accept().use { socket ->
                val remote = socket.remoteSocketAddress as InetSocketAddress
                observed.complete(attributions.claim(TrafficEndpoint(remote.address.hostAddress, remote.port)))
                readHeader(socket)
                socket.getOutputStream().write(
                    "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK".encodeToByteArray(),
                )
            }
        }.apply { isDaemon = true; start() }
        val gateway = WifiLanProxyGateway(
            bindHost = sourceAddress,
            bindPort = gatewayPort,
            targetProxy = { InetSocketAddress("127.0.0.1", internalProxy.localPort) },
            attributions = attributions,
            nowMillis = System::currentTimeMillis,
        )
        try {
            gateway.start()
            assertContains(rawProxyRequest(sourceAddress, gatewayPort), "200 OK")
            val ingress = observed.get(5L, TimeUnit.SECONDS)
            assertIs<IngressKind.WifiLanClient>(ingress?.kind)
            assertEquals(sourceAddress, ingress?.clientIdentity?.value)
        } finally {
            gateway.close()
            internalProxy.close()
            internalWorker.join(5_000L)
        }
    }

    @Test
    fun `desktop runtime automatically follows proxy lifecycle`() = runBlocking {
        val address = localLanAddress()
        val proxyPort = freePort(address.address)
        val monitor = DesktopNetworkSnapshotMonitor(
            scanner = DesktopNetworkScanner {
                DesktopNetworkObservation(listOf(address), defaultRouteAvailable = true, vpnActive = false)
            },
            pollIntervalMillis = 60_000L,
            dispatcher = Dispatchers.Unconfined,
        )
        val proxy = RunningProxyRuntime(proxyPort)
        val connectivity = DesktopConnectivityRuntime(proxy, monitor, Dispatchers.Unconfined)
        val runtime = DesktopWifiSharingRuntime(
            proxyRuntime = proxy,
            connectivityRuntime = connectivity,
            attributions = IngressAttributionRegistry(),
            certificateDer = { byteArrayOf(1, 2, 3) },
            dispatcher = Dispatchers.Unconfined,
        )
        try {
            val active = withTimeout(5_000L) { runtime.state.first { it is WifiSharingState.Active } }
            assertEquals(proxyPort, (active as WifiSharingState.Active).session.proxyEndpoint.port)
            assertTrue(active.session.setupUrl.endsWith("/setup"))
            assertTrue(
                connectivity.context.value.proxyEndpoints.endpoints.any { endpoint ->
                    endpoint.scope == ProxyEndpointScope.LAN &&
                        endpoint.accessRequirement == ProxyAccessRequirement.OPEN_LAN_CLIENT
                },
            )

            proxy.stop(ProxyStopReason.USER_REQUEST)
            withTimeout(5_000L) { runtime.state.first { it is WifiSharingState.Disabled } }
            withTimeout(5_000L) {
                connectivity.context.first { context ->
                    context.proxyEndpoints.endpoints.none { endpoint -> endpoint.scope == ProxyEndpointScope.LAN }
                }
            }

            proxy.restart()
            val restarted = withTimeout(5_000L) { runtime.state.first { it is WifiSharingState.Active } }
            assertEquals(proxyPort, (restarted as WifiSharingState.Active).session.proxyEndpoint.port)
        } finally {
            runtime.close()
            connectivity.close()
        }
        Unit
    }

    private class RunningProxyRuntime(port: Int) : ProxyRuntimePort {
        private val mutableState = MutableStateFlow<ProxyRuntimeState>(runningState(port))
        override val state: StateFlow<ProxyRuntimeState> = mutableState

        override suspend fun start(configuration: ProxyRuntimeConfiguration): ProxyStartResult =
            ProxyStartResult.Running((mutableState.value as ProxyRuntimeState.Running).handle)

        override suspend fun stop(reason: ProxyStopReason): ProxyStopResult {
            mutableState.value = ProxyRuntimeState.Stopped
            return ProxyStopResult.Stopped
        }

        fun restart() {
            val endpoint = (initialState as ProxyRuntimeState.Running).handle
            mutableState.value = ProxyRuntimeState.Running(endpoint)
        }

        private val initialState: ProxyRuntimeState = mutableState.value

        private fun runningState(port: Int): ProxyRuntimeState.Running = ProxyRuntimeState.Running(
            ProxyRuntimeHandle(
                runtimeId = "wifi-test-runtime",
                endpoints = ProxyEndpointSnapshot(
                    ProxyEndpointVersion(1L),
                    listOf(
                        ProxyEndpoint(
                            "127.0.0.1",
                            port,
                            ProxyEndpointScope.LOOPBACK,
                            ProxyAccessRequirement.LOCAL_PROCESS,
                        ),
                    ),
                ),
            ),
        )
    }

    private fun localLanAddress(): NetworkAddress {
        val match = NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { networkInterface ->
                networkInterface.inetAddresses.toList().map { address -> networkInterface to address }
            }
            .firstOrNull { (_, address) -> address is Inet4Address && !address.isLoopbackAddress }
            ?: error("Wi-Fi backend test requires one non-loopback IPv4 interface.")
        return NetworkAddress(
            interfaceId = match.first.name,
            address = match.second.hostAddress,
            family = NetworkAddressFamily.IPV4,
            loopback = false,
        )
    }

    private fun freePort(host: String): Int = ServerSocket().use { socket ->
        socket.bind(InetSocketAddress(host, 0))
        socket.localPort
    }

    private fun rawGet(host: String, port: Int, path: String, authority: String): String =
        Socket(host, port).use { socket ->
            socket.soTimeout = 5_000
            socket.getOutputStream().write(
                "GET $path HTTP/1.1\r\nHost: $authority\r\nConnection: close\r\n\r\n".encodeToByteArray(),
            )
            socket.getOutputStream().flush()
            socket.getInputStream().readBytes().decodeToString()
        }

    private fun rawProxyRequest(host: String, port: Int): String = Socket(host, port).use { socket ->
        socket.soTimeout = 5_000
        socket.getOutputStream().write(
            "GET http://example.test/ HTTP/1.1\r\nHost: example.test\r\nConnection: close\r\n\r\n".encodeToByteArray(),
        )
        socket.getOutputStream().flush()
        socket.getInputStream().readBytes().decodeToString()
    }

    private fun readHeader(socket: Socket): ByteArray {
        val output = ArrayList<Byte>()
        var tail = ""
        while (!tail.endsWith("\r\n\r\n")) {
            val value = socket.getInputStream().read()
            if (value < 0) break
            output += value.toByte()
            tail = (tail + value.toChar()).takeLast(4)
        }
        return output.toByteArray()
    }
}
