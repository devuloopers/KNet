package com.devuloopers.knet.connectivity.desktop.wifi

import com.devuloopers.knet.application.port.connectivity.wifi.WifiClientApprovalResult
import com.devuloopers.knet.application.port.connectivity.wifi.WifiInvitationResult
import com.devuloopers.knet.application.port.connectivity.wifi.WifiSharingOperationResult
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
import com.devuloopers.knet.connectivity.model.WifiSharingActionReason
import com.devuloopers.knet.connectivity.model.WifiSharingConfiguration
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
import java.util.concurrent.atomic.AtomicReference
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WifiSharingBackendTest {
    @Test
    fun `invitation is bounded to its first source and expires`() {
        var now = 1_000L
        val invitations = WifiInvitationService(
            setupBaseUrl = "http://192.0.2.10:8181",
            nowMillis = { now },
            invitationLifetimeMillis = 2_000L,
        )
        val invitation = invitations.create()
        val token = invitation.setupUrl.substringAfterLast('/')

        assertNotNull(invitations.claim(token, "192.0.2.20"))
        assertNotNull(invitations.claim(token, "192.0.2.20"))
        assertNull(invitations.claim(token, "192.0.2.21"))

        now = invitation.expiresAtEpochMillis
        assertNull(invitations.claim(token, "192.0.2.20"))
    }

    @Test
    fun `approval registry expires candidates and revokes session clients`() {
        var now = 1_000L
        val registry = WifiClientApprovalRegistry(
            nowMillis = { now },
            pendingLifetimeMillis = 2_000L,
        )
        val candidate = assertNotNull(registry.observe("192.0.2.20"))
        assertEquals(6, candidate.confirmationCode.length)
        val approved = assertNotNull(registry.approve(candidate.id, "Test phone"))
        assertEquals(approved, registry.approvedFor("192.0.2.20"))
        assertEquals(approved, registry.revoke(approved.id))
        assertNull(registry.approvedFor("192.0.2.20"))

        val expiring = assertNotNull(registry.observe("192.0.2.21"))
        now = expiring.expiresAtEpochMillis
        assertTrue(registry.snapshot().pendingClients.isEmpty())
    }

    @Test
    fun `tokenized setup portal requires desktop approval before serving artifacts`() {
        val address = localLanAddress()
        val setupPort = freePort(address.address)
        val endpoint = ProxyEndpoint(
            address.address,
            freePort(address.address),
            ProxyEndpointScope.LAN,
            ProxyAccessRequirement.APPROVED_LAN_CLIENT,
        )
        val invitations = WifiInvitationService("http://${address.address}:$setupPort", System::currentTimeMillis)
        val approvals = WifiClientApprovalRegistry(System::currentTimeMillis)
        val portal = WifiSetupPortal(
            bindHost = address.address,
            bindPort = setupPort,
            proxyEndpoint = endpoint,
            certificateDer = { byteArrayOf(1, 2, 3) },
            invitations = invitations,
            approvals = approvals,
        )
        try {
            portal.start()
            val invitation = invitations.create()
            val path = java.net.URI.create(invitation.setupUrl).path
            val token = path.substringAfterLast('/')

            val pendingPage = rawGet(address.address, setupPort, path, address.address)
            assertContains(pendingPage, "approve this phone")
            assertContains(rawGet(address.address, setupPort, "$path/knet-ca.crt", address.address), "403")
            assertContains(rawGet(address.address, setupPort, path, "untrusted.example"), "421")

            val candidate = approvals.snapshot().pendingClients.single()
            assertNotNull(approvals.approve(candidate.id, "Portal phone"))
            assertContains(rawGet(address.address, setupPort, path, address.address), "Approved")
            assertContains(rawGet(address.address, setupPort, "$path/proxy.pac", address.address), "PROXY")
            assertContains(rawGet(address.address, setupPort, "/invite/$token/missing", address.address), "404")
        } finally {
            portal.close()
        }
    }

    @Test
    fun `approved LAN stream is attributed and revocation denies reconnect`() = runBlocking {
        val address = localLanAddress()
        val sourceAddress = address.address
        val approvals = WifiClientApprovalRegistry(System::currentTimeMillis)
        val candidate = assertNotNull(approvals.observe(sourceAddress))
        val client = assertNotNull(approvals.approve(candidate.id, "Gateway phone"))
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
            approvals = approvals,
            attributions = attributions,
            nowMillis = System::currentTimeMillis,
        )
        try {
            gateway.start()
            val response = rawProxyRequest(sourceAddress, gatewayPort)
            assertContains(response, "200 OK")
            val ingress = observed.get(5L, TimeUnit.SECONDS)
            assertIs<IngressKind.WifiApprovedDevice>(ingress?.kind)
            assertEquals(client.id.value, ingress?.clientIdentity?.value)

            assertNotNull(approvals.revoke(client.id))
            gateway.revoke(client.id)
            assertContains(rawProxyRequest(sourceAddress, gatewayPort), "403 Forbidden")
        } finally {
            gateway.close()
            internalProxy.close()
            internalWorker.join(5_000L)
        }
    }

    @Test
    fun `desktop runtime publishes LAN endpoint then invalidates it on network change`() = runBlocking {
        val address = localLanAddress()
        val observation = AtomicReference(
            DesktopNetworkObservation(listOf(address), defaultRouteAvailable = true, vpnActive = false),
        )
        val monitor = DesktopNetworkSnapshotMonitor(
            scanner = DesktopNetworkScanner { observation.get() },
            pollIntervalMillis = 60_000L,
            dispatcher = Dispatchers.Unconfined,
        )
        val proxy = RunningProxyRuntime()
        val connectivity = DesktopConnectivityRuntime(proxy, monitor, Dispatchers.Unconfined)
        val runtime = DesktopWifiSharingRuntime(
            proxyRuntime = proxy,
            connectivityRuntime = connectivity,
            attributions = IngressAttributionRegistry(),
            certificateDer = { byteArrayOf(1, 2, 3) },
            dispatcher = Dispatchers.Unconfined,
        )
        try {
            withTimeout(5_000L) {
                connectivity.context.first { it.proxyEndpoints.endpoints.any { endpoint -> endpoint.scope == ProxyEndpointScope.LOOPBACK } }
            }
            val proxyPort = freePort(address.address)
            var setupPort = freePort(address.address)
            while (setupPort == proxyPort) setupPort = freePort(address.address)
            val result = runtime.enable(
                WifiSharingConfiguration(
                    networkAddress = address,
                    proxyPort = proxyPort,
                    setupPort = setupPort,
                ),
            )
            assertIs<WifiSharingOperationResult.Succeeded>(result)
            assertIs<WifiSharingState.Active>(runtime.state.value)
            withTimeout(5_000L) {
                connectivity.context.first { context ->
                    context.proxyEndpoints.endpoints.any { endpoint -> endpoint.scope == ProxyEndpointScope.LAN }
                }
            }

            val invitation = assertIs<WifiInvitationResult.Created>(runtime.createInvitation()).invitation
            val invitationPath = java.net.URI.create(invitation.setupUrl).path
            rawGet(address.address, java.net.URI.create(invitation.setupUrl).port, invitationPath, address.address)
            val pending = withTimeout(5_000L) {
                runtime.state.first { state -> state is WifiSharingState.Active && state.pendingClients.isNotEmpty() }
            } as WifiSharingState.Active
            assertIs<WifiClientApprovalResult.Approved>(
                runtime.approve(pending.pendingClients.single().id, "Runtime phone"),
            )

            observation.set(DesktopNetworkObservation(emptyList(), defaultRouteAvailable = false, vpnActive = false))
            monitor.refresh()
            val invalidated = withTimeout(5_000L) {
                runtime.state.first { it is WifiSharingState.NeedsUserAction }
            }
            assertEquals(
                WifiSharingActionReason.ADDRESS_REMOVED,
                (invalidated as WifiSharingState.NeedsUserAction).reason,
            )
            withTimeout(5_000L) {
                connectivity.context.first { context ->
                    context.proxyEndpoints.endpoints.none { endpoint -> endpoint.scope == ProxyEndpointScope.LAN }
                }
            }
        } finally {
            runtime.close()
            connectivity.close()
        }
        Unit
    }

    @Test
    fun `setup bind failure rolls back gateway and publishes no LAN endpoint`() = runBlocking {
        val address = localLanAddress()
        val observation = AtomicReference(
            DesktopNetworkObservation(listOf(address), defaultRouteAvailable = true, vpnActive = false),
        )
        val monitor = DesktopNetworkSnapshotMonitor(
            scanner = DesktopNetworkScanner { observation.get() },
            pollIntervalMillis = 60_000L,
            dispatcher = Dispatchers.Unconfined,
        )
        val proxy = RunningProxyRuntime()
        val connectivity = DesktopConnectivityRuntime(proxy, monitor, Dispatchers.Unconfined)
        val runtime = DesktopWifiSharingRuntime(
            proxyRuntime = proxy,
            connectivityRuntime = connectivity,
            attributions = IngressAttributionRegistry(),
            certificateDer = { byteArrayOf(1, 2, 3) },
            dispatcher = Dispatchers.Unconfined,
        )
        val occupiedSetup = ServerSocket()
        try {
            withTimeout(5_000L) {
                connectivity.context.first { context ->
                    context.proxyEndpoints.endpoints.any { endpoint -> endpoint.scope == ProxyEndpointScope.LOOPBACK }
                }
            }
            occupiedSetup.bind(InetSocketAddress(address.address, 0))
            val setupPort = occupiedSetup.localPort
            var gatewayPort = freePort(address.address)
            while (gatewayPort == setupPort) gatewayPort = freePort(address.address)

            val result = runtime.enable(
                WifiSharingConfiguration(
                    networkAddress = address,
                    proxyPort = gatewayPort,
                    setupPort = setupPort,
                ),
            )

            assertEquals("wifi_bind_failed", assertIs<WifiSharingOperationResult.Rejected>(result).code)
            assertIs<WifiSharingState.Failed>(runtime.state.value)
            assertTrue(
                connectivity.context.value.proxyEndpoints.endpoints.none { endpoint ->
                    endpoint.scope == ProxyEndpointScope.LAN
                },
            )
            ServerSocket().use { releasedGateway ->
                releasedGateway.bind(InetSocketAddress(address.address, gatewayPort))
            }
        } finally {
            occupiedSetup.close()
            runtime.close()
            connectivity.close()
        }
        Unit
    }

    private class RunningProxyRuntime : ProxyRuntimePort {
        private val mutableState = MutableStateFlow<ProxyRuntimeState>(
            ProxyRuntimeState.Running(
                ProxyRuntimeHandle(
                    runtimeId = "wifi-test-runtime",
                    endpoints = ProxyEndpointSnapshot(
                        ProxyEndpointVersion(1L),
                        listOf(
                            ProxyEndpoint(
                                "127.0.0.1",
                                8_080,
                                ProxyEndpointScope.LOOPBACK,
                                ProxyAccessRequirement.LOCAL_PROCESS,
                            ),
                        ),
                    ),
                ),
            ),
        )
        override val state: StateFlow<ProxyRuntimeState> = mutableState

        override suspend fun start(configuration: ProxyRuntimeConfiguration): ProxyStartResult =
            ProxyStartResult.Running((mutableState.value as ProxyRuntimeState.Running).handle)

        override suspend fun stop(reason: ProxyStopReason): ProxyStopResult {
            mutableState.value = ProxyRuntimeState.Stopped
            return ProxyStopResult.Stopped
        }
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

    private fun freePort(host: String): Int {
        val socket = ServerSocket()
        return socket.use {
            it.bind(InetSocketAddress(host, 0))
            it.localPort
        }
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
