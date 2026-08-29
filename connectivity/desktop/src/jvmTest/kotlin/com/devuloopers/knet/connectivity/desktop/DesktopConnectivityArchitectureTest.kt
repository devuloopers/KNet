package com.devuloopers.knet.connectivity.desktop

import com.devuloopers.knet.connectivity.desktop.artifact.SetupArtifactStore
import com.devuloopers.knet.connectivity.desktop.network.DesktopNetworkObservation
import com.devuloopers.knet.connectivity.desktop.network.DesktopNetworkScanner
import com.devuloopers.knet.connectivity.desktop.network.DesktopNetworkSnapshotMonitor
import com.devuloopers.knet.connectivity.desktop.network.DesktopLanAddressSelection
import com.devuloopers.knet.connectivity.desktop.network.DesktopLanAddressSelectionReason
import com.devuloopers.knet.connectivity.desktop.portal.DedicatedSetupPortal
import com.devuloopers.knet.connectivity.desktop.portal.SetupPortalContent
import com.devuloopers.knet.connectivity.desktop.provider.AppleProfileTemplateRenderer
import com.devuloopers.knet.connectivity.desktop.provider.AppleProfileSetupProvider
import com.devuloopers.knet.connectivity.desktop.provider.ManualProxySetupProvider
import com.devuloopers.knet.connectivity.desktop.provider.PacSetupProvider
import com.devuloopers.knet.connectivity.model.ConnectivityContext
import com.devuloopers.knet.connectivity.model.ConnectivityContextVersion
import com.devuloopers.knet.connectivity.model.NetworkAddress
import com.devuloopers.knet.connectivity.model.NetworkAddressFamily
import com.devuloopers.knet.connectivity.model.NetworkSnapshot
import com.devuloopers.knet.connectivity.model.ProxyAccessRequirement
import com.devuloopers.knet.connectivity.model.ProxyEndpoint
import com.devuloopers.knet.connectivity.model.ProxyEndpointScope
import com.devuloopers.knet.connectivity.model.ProxyEndpointSnapshot
import com.devuloopers.knet.connectivity.model.ProxyEndpointVersion
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertIs

class DesktopConnectivityArchitectureTest {
    @Test
    fun `PAC output is deterministic and context-versioned`() = kotlinx.coroutines.runBlocking {
        val store = SetupArtifactStore("http://127.0.0.1:8181")
        val provider = PacSetupProvider(store)
        val context = context(7L)

        val first = provider.describe(context)
        val second = provider.describe(context)

        assertEquals(first.artifacts, second.artifacts)
        assertEquals(
            "function FindProxyForURL(url, host) {\n  return \"PROXY 192.0.2.10:8080; DIRECT\";\n}\n",
            store.get(first.artifacts.single().id)?.copyBytes()?.decodeToString(),
        )
    }

    @Test
    fun `Apple profile generation is resource backed deterministic and resolves typed values`() =
        kotlinx.coroutines.runBlocking {
            val store = SetupArtifactStore("http://127.0.0.1:8181")
            val provider = AppleProfileSetupProvider(store) { byteArrayOf(1, 2, 3) }

            val descriptor = provider.describe(context(9L))
            val profile = store.get(descriptor.artifacts.single().id)!!.copyBytes().decodeToString()

            assertContains(profile, "<string>192.0.2.10</string>")
            assertContains(profile, "<integer>8080</integer>")
            assertContains(profile, "AQID")
            assertContains(profile, "<!DOCTYPE plist PUBLIC")
            assertFalse("{{" in profile)
            assertEquals(profile, provider.generateProfile(context(9L).preferredEndpoint(), byteArrayOf(1, 2, 3)))
        }

    @Test
    fun `Apple profile template escapes serialized string values`() {
        val profile = AppleProfileTemplateRenderer.render("proxy&edge.example", 8080, byteArrayOf(1, 2, 3))

        assertContains(profile, "<string>proxy&amp;edge.example</string>")
        assertFalse("proxy&edge.example" in profile)
    }

    @Test
    fun `network version changes only for meaningful transitions`() {
        val observation = AtomicReference(
            DesktopNetworkObservation(
                listOf(NetworkAddress("lo0", "127.0.0.1", NetworkAddressFamily.IPV4, true)),
                defaultRouteAvailable = false,
                vpnActive = false,
            ),
        )
        val monitor = DesktopNetworkSnapshotMonitor(
            scanner = DesktopNetworkScanner { observation.get() },
            pollIntervalMillis = 60_000L,
            nowMillis = { 10L },
            dispatcher = Dispatchers.Unconfined,
        )
        try {
            assertEquals(0L, monitor.refresh().version)
            observation.set(observation.get().copy(vpnActive = true))
            assertEquals(1L, monitor.refresh().version)
            assertEquals(1L, monitor.refresh().version)
        } finally {
            monitor.close()
        }
    }

    @Test
    fun `network version and preferred address follow a default route change`() {
        val ethernet = NetworkAddress("eth0", "10.0.0.2", NetworkAddressFamily.IPV4, false)
        val wifi = NetworkAddress("wlan0", "192.168.1.2", NetworkAddressFamily.IPV4, false)
        val observation = AtomicReference(
            DesktopNetworkObservation(
                addresses = listOf(ethernet, wifi),
                defaultRouteAvailable = true,
                vpnActive = false,
                selection = DesktopLanAddressSelection(
                    ethernet,
                    DesktopLanAddressSelectionReason.DEFAULT_ROUTE,
                ),
            ),
        )
        val monitor = DesktopNetworkSnapshotMonitor(
            scanner = DesktopNetworkScanner { observation.get() },
            pollIntervalMillis = 60_000L,
            nowMillis = { 10L },
            dispatcher = Dispatchers.Unconfined,
        )
        try {
            assertEquals(ethernet, monitor.snapshots.value.preferredLanAddress)
            observation.set(
                observation.get().copy(
                    selection = DesktopLanAddressSelection(
                        wifi,
                        DesktopLanAddressSelectionReason.DEFAULT_ROUTE,
                    ),
                ),
            )

            assertEquals(1L, monitor.refresh().version)
            assertEquals(wifi, monitor.snapshots.value.preferredLanAddress)
        } finally {
            monitor.close()
        }
    }

    @Test
    fun `phone setup provider does not advertise a loopback-only proxy`() = kotlinx.coroutines.runBlocking {
        val provider = ManualProxySetupProvider()

        assertIs<com.devuloopers.knet.connectivity.model.ConnectivityAvailability.NetworkUnavailable>(
            provider.availability(loopbackContext()).first(),
        )
        Unit
    }

    @Test
    fun `dedicated portal rejects unknown authority and serves versioned artifact`() {
        val port = ServerSocket(0).use { it.localPort }
        val artifacts = SetupArtifactStore("http://127.0.0.1:$port")
        val artifact = artifacts.put(
            "pac",
            ConnectivityContextVersion(1L),
            "application/x-ns-proxy-autoconfig",
            "pac",
            "PAC".encodeToByteArray(),
        )
        val portal = DedicatedSetupPortal(
            "127.0.0.1",
            port,
            setOf("knet.local"),
            artifacts,
            SetupPortalContent({ _, _ -> "INDEX" }, { byteArrayOf(1) }),
        )
        try {
            portal.start()
            assertContains(rawGet(port, "/setup", "upstream.example"), "421")
            assertContains(rawGet(port, "/setup", "knet.local"), "INDEX")
            assertContains(rawGet(port, "/artifacts/${artifact.id.value}", "knet.local"), "PAC")
            assertNotEquals(rawGet(port, "/setup", "upstream.example"), rawGet(port, "/setup", "knet.local"))
        } finally {
            portal.close()
        }
    }

    private fun context(version: Long): ConnectivityContext = ConnectivityContext(
        ConnectivityContextVersion(version),
        ProxyEndpointSnapshot(
            ProxyEndpointVersion(2L),
            listOf(
                ProxyEndpoint(
                    "192.0.2.10",
                    8080,
                    ProxyEndpointScope.LAN,
                    ProxyAccessRequirement.OPEN_LAN_CLIENT,
                ),
            ),
        ),
        NetworkSnapshot(
            1L,
            listOf(NetworkAddress("en0", "192.0.2.10", NetworkAddressFamily.IPV4, false)),
            defaultRouteAvailable = true,
            vpnActive = false,
            observedAtEpochMillis = 1L,
        ),
    )

    private fun loopbackContext(): ConnectivityContext = ConnectivityContext(
        ConnectivityContextVersion(1L),
        ProxyEndpointSnapshot(
            ProxyEndpointVersion(1L),
            listOf(
                ProxyEndpoint(
                    "127.0.0.1",
                    8080,
                    ProxyEndpointScope.LOOPBACK,
                    ProxyAccessRequirement.LOCAL_PROCESS,
                ),
            ),
        ),
        NetworkSnapshot(
            1L,
            listOf(NetworkAddress("lo0", "127.0.0.1", NetworkAddressFamily.IPV4, true)),
            defaultRouteAvailable = true,
            vpnActive = false,
            observedAtEpochMillis = 1L,
        ),
    )

    private fun ConnectivityContext.preferredEndpoint(): ProxyEndpoint = proxyEndpoints.endpoints.single()

    private fun rawGet(port: Int, path: String, host: String): String = Socket("127.0.0.1", port).use { socket ->
        socket.getOutputStream().write("GET $path HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n\r\n".encodeToByteArray())
        socket.getOutputStream().flush()
        socket.getInputStream().bufferedReader().readText()
    }
}
