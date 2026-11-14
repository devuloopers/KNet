package com.devuloopers.knet.companion.application.usecase

import com.devuloopers.knet.companion.application.contract.*
import com.devuloopers.knet.companion.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

/** Ensures the initial readiness probe cannot hide control loss while a VPN run remains enabled. */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveCompanionConnectionUseCaseTest {
    @Test fun matchingRunningVpnProjectsLossRecoveryAndRevocationWithoutStoppingInspection() = runTest {
        val fixture = Fixture()
        val observed = mutableListOf<CompanionConnectionState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fixture.observe.state.collect { observed += it } }
        assertEquals(fixture.connected, fixture.observe.currentState)
        fixture.session.value = fixture.observation(CompanionSessionPhase.RECONNECTING)
        runCurrent()
        assertIs<CompanionConnectionState.Reconnecting>(observed.last())
        assertIs<CompanionInspectionState.Running>(fixture.inspection.state.value)
        fixture.session.value = fixture.observation(CompanionSessionPhase.CONNECTED)
        runCurrent()
        assertEquals(fixture.connected, observed.last())
        fixture.session.value = fixture.observation(CompanionSessionPhase.REJECTED)
        runCurrent()
        assertEquals(CompanionFailureCode.PAIRING_REJECTED, assertIs<CompanionConnectionState.Failed>(observed.last()).failure.code)
        fixture.inspectionState.value = CompanionInspectionState.Stopped
        runCurrent()
        assertEquals(fixture.connected, observed.last(), "An inactive VPN has no authoritative control channel")
    }

    @Test fun differentDesktopAndIdleControlNeverOverrideSelectedCarrier() {
        val fixture = Fixture()
        fixture.session.value = fixture.observation(CompanionSessionPhase.RECONNECTING).copy(desktopId = CompanionDesktopId("other"))
        assertEquals(fixture.connected, fixture.observe.currentState)
        fixture.session.value = null
        assertEquals(fixture.connected, fixture.observe.currentState)
        fixture.carrierState.value = CompanionConnectionState.Disconnected
        fixture.session.value = fixture.observation(CompanionSessionPhase.CONNECTED)
        assertEquals(CompanionConnectionState.Disconnected, fixture.observe.currentState)
    }

    private class Fixture {
        val desktop = CompanionDesktopId("desktop")
        val connected = CompanionConnectionState.Connected(desktop, CompanionTransportKind.DIRECT_LAN, 1_000L)
        val carrierState = MutableStateFlow<CompanionConnectionState>(connected)
        val carrier = object : CompanionTransport {
            override val state = carrierState
            override suspend fun connect(registration: CompanionRegistration, credential: String) = CompanionTransportResult.Connected
            override suspend fun disconnect() { state.value = CompanionConnectionState.Disconnected }
        }
        val inspectionState = MutableStateFlow<CompanionInspectionState>(CompanionInspectionState.Running(CompanionInspectionMode.DEVICE_VPN, 1_000L, true))
        val inspection = object : CompanionInspectionController {
            override val state = inspectionState
            override suspend fun prepare() = CompanionInspectionPreparationResult.Ready
            override suspend fun start(configuration: CompanionInspectionConfiguration) = CompanionInspectionStartResult.Started
            override suspend fun stop() { state.value = CompanionInspectionState.Stopped }
        }
        val session = MutableStateFlow<CompanionSessionObservation?>(null)
        val observe = ObserveCompanionConnectionUseCase(carrier, session, inspection)
        fun observation(phase: CompanionSessionPhase) = CompanionSessionObservation(desktop, "run", phase)
    }
}
