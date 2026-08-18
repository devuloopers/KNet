package com.devuloopers.knet.connectivity.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ConnectivityModelsTest {

    @Test
    fun `availability lifecycle and health remain independent values`() {
        val availability: ConnectivityAvailability = ConnectivityAvailability.Available
        val lifecycle: ConnectivityLifecycle = ConnectivityLifecycle.Active("session-1")
        val health: ConnectivityHealth = ConnectivityHealth.Degraded("device-disconnected")

        assertIs<ConnectivityAvailability.Available>(availability)
        assertIs<ConnectivityLifecycle.Active>(lifecycle)
        assertIs<ConnectivityHealth.Degraded>(health)
    }

    @Test
    fun `proxy endpoint validates its TCP port`() {
        assertFailsWith<IllegalArgumentException> {
            ProxyEndpoint(
                host = "127.0.0.1",
                port = 0,
                scope = ProxyEndpointScope.LOOPBACK,
                accessRequirement = ProxyAccessRequirement.LOCAL_PROCESS,
            )
        }
    }

    @Test
    fun `setup descriptor preserves ordered typed steps`() {
        val endpoint = ProxyEndpoint(
            host = "127.0.0.1",
            port = 8080,
            scope = ProxyEndpointScope.LOOPBACK,
            accessRequirement = ProxyAccessRequirement.LOCAL_PROCESS,
        )
        val descriptor = SetupDescriptor(
            mechanismId = ConnectivityMechanismId("manual"),
            titleToken = "connectivity.manual.title",
            summaryToken = "connectivity.manual.summary",
            capabilities = setOf(ConnectivityCapability.MANUAL_CONFIGURATION),
            steps = listOf(SetupStep.ConfigureProxy(endpoint)),
            artifacts = emptyList(),
            contextVersion = ConnectivityContextVersion(1L),
        )

        assertEquals(SetupStep.ConfigureProxy(endpoint), descriptor.steps.single())
    }

    @Test
    fun `Wi-Fi sharing configuration requires an exact non-loopback address`() {
        assertFailsWith<IllegalArgumentException> {
            WifiSharingConfiguration(
                networkAddress = NetworkAddress("lo0", "127.0.0.1", NetworkAddressFamily.IPV4, loopback = true),
                proxyPort = 8_080,
            )
        }

        val configuration = WifiSharingConfiguration(
            networkAddress = NetworkAddress("en0", "192.0.2.10", NetworkAddressFamily.IPV4, loopback = false),
            proxyPort = 8_080,
        )

        assertEquals(8_181, configuration.setupPort)
    }
}
