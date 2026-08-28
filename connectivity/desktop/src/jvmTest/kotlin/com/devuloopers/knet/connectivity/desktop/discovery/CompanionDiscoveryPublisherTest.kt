package com.devuloopers.knet.connectivity.desktop.discovery

import com.devuloopers.knet.application.usecase.pairing.CompanionDiscoveryEnvironment
import com.devuloopers.knet.application.usecase.pairing.CompanionDiscoveryEnvironmentProvider
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionDesktopRuntimeId
import com.devuloopers.knet.companion.model.CompanionDiscoveryTxtCodec
import com.devuloopers.knet.connectivity.desktop.gateway.CompanionControlGatewayFailure
import com.devuloopers.knet.connectivity.desktop.gateway.CompanionControlGatewayState
import com.devuloopers.knet.connectivity.model.NetworkAddress
import com.devuloopers.knet.connectivity.model.NetworkAddressFamily
import com.devuloopers.knet.connectivity.model.NetworkSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class CompanionDiscoveryPublisherTest {
    @Test
    fun listeningControlGatewayAdvertisesWithoutProxyAndTracksAddressChanges() = runTest {
        val network = MutableStateFlow(snapshot("192.168.1.2", version = 1))
        val gateway = MutableStateFlow<CompanionControlGatewayState>(CompanionControlGatewayState.Stopped)
        val registrations = mutableListOf<FakeRegistration>()
        val calls = mutableListOf<RegistrationCall>()
        val registrar = registrar(calls, registrations)
        val environment = environment()
        val publisher = CompanionDiscoveryPublisher(
            networkSnapshots = network,
            controlGatewayState = gateway,
            environmentProvider = CompanionDiscoveryEnvironmentProvider { environment },
            registrar = registrar,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        publisher.start()
        runCurrent()
        assertTrue(calls.isEmpty())

        gateway.value = CompanionControlGatewayState.Listening(environment.controlPort)
        runCurrent()
        network.value = snapshot("192.168.1.9", version = 2)
        runCurrent()

        assertEquals(listOf("192.168.1.2", "192.168.1.9"), calls.map(RegistrationCall::address))
        assertEquals(listOf(environment.controlPort, environment.controlPort), calls.map(RegistrationCall::port))
        assertTrue(registrations.first().closed)
        assertEquals(environment.desktopId, CompanionDiscoveryTxtCodec().decode(calls.last().txt).desktopId)

        gateway.value = CompanionControlGatewayState.Failed(CompanionControlGatewayFailure.LISTENER_FAILED)
        runCurrent()

        assertTrue(registrations.last().closed)
        publisher.close()
    }

    @Test
    fun gatewayIsNotAdvertisedUntilBothListenerAndLanAddressAreAvailable() = runTest {
        val network = MutableStateFlow(snapshot(address = null, version = 1))
        val gateway = MutableStateFlow<CompanionControlGatewayState>(CompanionControlGatewayState.Listening(CONTROL_PORT))
        val registrations = mutableListOf<FakeRegistration>()
        val calls = mutableListOf<RegistrationCall>()
        val publisher = CompanionDiscoveryPublisher(
            networkSnapshots = network,
            controlGatewayState = gateway,
            environmentProvider = CompanionDiscoveryEnvironmentProvider(::environment),
            registrar = registrar(calls, registrations),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        publisher.start()
        runCurrent()
        assertTrue(calls.isEmpty())

        network.value = snapshot("192.168.1.4", version = 2)
        runCurrent()
        assertEquals(1, calls.size)

        gateway.value = CompanionControlGatewayState.Stopped
        runCurrent()
        assertTrue(registrations.single().closed)
        publisher.close()
    }

    private fun registrar(
        calls: MutableList<RegistrationCall>,
        registrations: MutableList<FakeRegistration>,
    ): CompanionDiscoveryRegistrar = CompanionDiscoveryRegistrar { address, name, port, txt ->
        calls += RegistrationCall(address, name, port, txt)
        FakeRegistration().also(registrations::add)
    }

    private class FakeRegistration : CompanionDiscoveryRegistration {
        var closed = false
        override fun close() {
            closed = true
        }
    }

    private data class RegistrationCall(
        val address: String,
        val name: String,
        val port: Int,
        val txt: Map<String, String>,
    )

    private companion object {
        const val CONTROL_PORT: Int = 8183

        fun environment() = CompanionDiscoveryEnvironment(
            desktopId = CompanionDesktopId("4ac0c20a-65e2-4bd8-ad63-122567fdb5e0"),
            legacyDesktopIds = setOf(CompanionDesktopId("knet-${"a".repeat(64)}")),
            runtimeId = CompanionDesktopRuntimeId.parse("f9a4ed22-f9c9-4b87-9b27-55efab33a84d"),
            controlPort = CONTROL_PORT,
            proxyPort = 8182,
        )

        fun snapshot(address: String?, version: Long) = NetworkSnapshot(
            version = version,
            addresses = address?.let {
                listOf(NetworkAddress("en0", it, NetworkAddressFamily.IPV4, loopback = false))
            }.orEmpty(),
            defaultRouteAvailable = address != null,
            vpnActive = false,
            observedAtEpochMillis = version,
        )
    }
}
