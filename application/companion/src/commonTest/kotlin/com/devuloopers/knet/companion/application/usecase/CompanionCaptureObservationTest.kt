package com.devuloopers.knet.companion.application.usecase

import com.devuloopers.knet.companion.application.contract.*
import com.devuloopers.knet.companion.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*

/** WSS and extension-process HTTPS paths share status semantics without inventing session liveness. */
@OptIn(ExperimentalCoroutinesApi::class)
class CompanionCaptureObservationTest {
    @Test fun `known WSS readiness avoids polling and stops immediately with inspection`() = runTest {
        val fixture = Fixture()
        fixture.session.value = fixture.frame(CompanionDesktopCaptureState.CAPTURING)
        val observed = mutableListOf<CompanionDesktopCaptureObservation?>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fixture.observe.execute().collect { observed += it } }
        advanceTimeBy(20_000); runCurrent()
        assertEquals(0, fixture.reads); assertEquals(CompanionDesktopCaptureState.CAPTURING, observed.last()?.status)
        fixture.session.value = fixture.frame(CompanionDesktopCaptureState.PAUSED)
        runCurrent(); assertEquals(CompanionDesktopCaptureState.PAUSED, observed.last()?.status)
        fixture.inspection.mutable.value = CompanionInspectionState.Stopped
        runCurrent(); assertNull(observed.last()); advanceTimeBy(60_000); runCurrent(); assertEquals(0, fixture.reads)
        job.cancelAndJoin()
    }
    @Test fun `extension owned or legacy session uses bounded HTTPS and stops reads after inspection`() = runTest {
        val fixture = Fixture(); val observed = mutableListOf<CompanionDesktopCaptureObservation?>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fixture.observe.execute().collect { observed += it } }
        advanceTimeBy(2000); runCurrent()
        assertEquals(1, fixture.reads); assertEquals(CompanionDesktopCaptureState.PAUSED, observed.last()?.status)
        fixture.status = CompanionDesktopCaptureState.CAPTURING
        advanceTimeBy(5000); runCurrent(); assertEquals(CompanionDesktopCaptureState.CAPTURING, observed.last()?.status)
        fixture.inspection.mutable.value = CompanionInspectionState.Stopped; runCurrent()
        val reads = fixture.reads; advanceTimeBy(60_000); runCurrent(); assertEquals(reads, fixture.reads)
        assertNull(observed.last()); job.cancelAndJoin()
    }
    @Test fun `unknown legacy replies back off and another desktop frame cannot become current readiness`() = runTest {
        val fixture = Fixture(); fixture.status = CompanionDesktopCaptureState.UNKNOWN
        fixture.session.value = fixture.frame(CompanionDesktopCaptureState.CAPTURING).copy(desktopId = CompanionDesktopId("foreign"))
        val observed = mutableListOf<CompanionDesktopCaptureObservation?>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fixture.observe.execute().collect { observed += it } }
        advanceTimeBy(2000); runCurrent()
        assertEquals(CompanionDesktopCaptureState.UNKNOWN, observed.last()?.status)
        advanceTimeBy(29_000); runCurrent(); assertEquals(1, fixture.reads)
        advanceTimeBy(1000); runCurrent(); assertEquals(2, fixture.reads)
        job.cancelAndJoin()
    }
    private class Inspection : CompanionInspectionController {
            val mutable = MutableStateFlow<CompanionInspectionState>(CompanionInspectionState.Running(CompanionInspectionMode.DEVICE_VPN, 1000, true))
            override val state: StateFlow<CompanionInspectionState> = mutable
            override suspend fun prepare() = error("read-only")
            override suspend fun start(configuration: CompanionInspectionConfiguration) = error("read-only")
            override suspend fun stop() = Unit
        }

    private class Fixture {
        val registration = CompanionUseCasesTest.registration()
        val session = MutableStateFlow<CompanionSessionObservation?>(null)
        var reads = 0
        var status = CompanionDesktopCaptureState.PAUSED
        val inspection = Inspection()
        val repository = object : CompanionRegistrationRepository {
            override val activeRegistration = MutableStateFlow<CompanionRegistration?>(registration)
            override val registrations = MutableStateFlow(listOf(registration))
            override suspend fun upsert(registration: CompanionRegistration, makeActive: Boolean) = Unit
            override suspend fun setActive(desktopId: CompanionDesktopId?) = false
            override suspend fun remove(desktopId: CompanionDesktopId): CompanionRegistration? = null
            override suspend fun migrateIdentity(previousDesktopId: CompanionDesktopId, registration: CompanionRegistration, makeActive: Boolean) = false
        }
        val credentials = object : CompanionCredentialStore {
            override suspend fun read(reference: CompanionCredentialReference) = "credential-for-test"
            override suspend fun write(reference: CompanionCredentialReference, credential: String) = Unit
            override suspend fun remove(reference: CompanionCredentialReference) = Unit
        }
        val observe = ObserveCompanionDesktopCaptureUseCase(session, repository, inspection,
            FetchCompanionCaptureStatusUseCase(credentials, CompanionControlTransport { reads++; CompanionControlResponse(200, CompanionCaptureStatusCodec.encode(status)) }))
        fun frame(status: CompanionDesktopCaptureState) = CompanionSessionObservation(registration.desktopId, "run", CompanionSessionPhase.CONNECTED, status)
    }
}
