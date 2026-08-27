package com.devuloopers.knet.connectivity.desktop.discovery

import com.devuloopers.knet.application.usecase.pairing.CompanionDiscoveryEnvironment
import com.devuloopers.knet.application.usecase.pairing.CompanionDiscoveryEnvironmentProvider
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionDesktopRuntimeId
import com.devuloopers.knet.companion.model.CompanionDiscoveryTxtCodec
import com.devuloopers.knet.connectivity.model.NetworkAddress
import com.devuloopers.knet.connectivity.model.NetworkAddressFamily
import com.devuloopers.knet.connectivity.model.ProxyAccessRequirement
import com.devuloopers.knet.connectivity.model.ProxyEndpoint
import com.devuloopers.knet.connectivity.model.ProxyEndpointScope
import com.devuloopers.knet.connectivity.model.WifiSharingSession
import com.devuloopers.knet.connectivity.model.WifiSharingSessionId
import com.devuloopers.knet.connectivity.model.WifiSharingState
import com.devuloopers.knet.connectivity.model.WifiSharingMetrics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class CompanionDiscoveryPublisherTest {
    @Test
    fun activeAddressIsReAdvertisedAndPreviousRegistrationIsClosed() = runTest {
        val state = MutableStateFlow<WifiSharingState>(WifiSharingState.Disabled(emptyList()))
        val registrations = mutableListOf<FakeRegistration>()
        val calls = mutableListOf<RegistrationCall>()
        val registrar = CompanionDiscoveryRegistrar { address, name, port, txt ->
            calls += RegistrationCall(address, name, port, txt)
            FakeRegistration().also(registrations::add)
        }
        val environment = environment()
        val publisher = CompanionDiscoveryPublisher(
            state,
            CompanionDiscoveryEnvironmentProvider { environment },
            registrar,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        publisher.start()
        state.value = active("192.168.1.2", 1)
        testScheduler.advanceUntilIdle()
        state.value = active("192.168.1.9", 2)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("192.168.1.2", "192.168.1.9"), calls.map(RegistrationCall::address))
        assertTrue(registrations.first().closed)
        assertEquals(environment.desktopId, CompanionDiscoveryTxtCodec().decode(calls.last().txt).desktopId)

        state.value = WifiSharingState.Disabled(emptyList())
        testScheduler.advanceUntilIdle()

        assertTrue(registrations.last().closed)
        publisher.close()
    }

    private class FakeRegistration : CompanionDiscoveryRegistration {
        var closed = false
        override fun close() { closed = true }
    }

    private data class RegistrationCall(
        val address: String,
        val name: String,
        val port: Int,
        val txt: Map<String, String>,
    )

    private companion object {
        fun environment() = CompanionDiscoveryEnvironment(
            desktopId = CompanionDesktopId("4ac0c20a-65e2-4bd8-ad63-122567fdb5e0"),
            legacyDesktopIds = setOf(CompanionDesktopId("knet-${"a".repeat(64)}")),
            runtimeId = CompanionDesktopRuntimeId.parse("f9a4ed22-f9c9-4b87-9b27-55efab33a84d"),
            controlPort = 8183,
            proxyPort = 8182,
        )

        fun active(address: String, version: Long) = WifiSharingState.Active(
            WifiSharingSession(
                id = WifiSharingSessionId("session-$version"),
                networkAddress = NetworkAddress("en0", address, NetworkAddressFamily.IPV4, false),
                proxyEndpoint = ProxyEndpoint(
                    host = address,
                    port = 8080,
                    scope = ProxyEndpointScope.LAN,
                    accessRequirement = ProxyAccessRequirement.OPEN_LAN_CLIENT,
                ),
                setupUrl = "http://$address:8181/setup",
                certificateSha256 = "a".repeat(64),
                networkVersion = version,
                startedAtEpochMillis = 1,
            ),
            WifiSharingMetrics(),
        )
    }
}
