package com.devuloopers.knet.companion.application.usecase

import com.devuloopers.knet.companion.application.contract.*
import com.devuloopers.knet.companion.model.*
import com.devuloopers.knet.identity.RegisteredDeviceId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

/** Virtual-time tests for the real policy-maintenance owner, including stale identity and read cancellation. */
@OptIn(ExperimentalCoroutinesApi::class)
class MaintainCompanionPacketPolicyUseCaseTest {
    @Test fun `one session applies changes only once and cancellation stops all polling`() = runTest {
        val repository = Repository()
        var policy = slow
        var reads = 0
        val applied = mutableListOf<PacketConditionConfiguration>()
        val useCase = MaintainCompanionPacketPolicyUseCase(CompanionPacketPolicySource { reads++; policy }, repository, testScheduler.timeSource)
        val job = backgroundScope.launch {
            useCase.execute(repository.activeRegistration.value!!, PacketConditionConfiguration.Disabled,
                CompanionPacketPolicySink { applied += it; PacketPolicyUpdateResult.APPLIED })
        }
        runCurrent()
        assertEquals(listOf(slow), applied)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(1, applied.size)
        policy = offline
        advanceTimeBy(250); runCurrent()
        assertEquals(listOf(slow, offline), applied)
        job.cancelAndJoin()
        val before = reads
        advanceTimeBy(5_000); runCurrent()
        assertEquals(before, reads)
    }

    @Test fun `expired policy lease releases stale offline shaping and later recovers`() = runTest {
        val repository = Repository()
        var available = false
        val applied = mutableListOf<PacketConditionConfiguration>()
        val useCase = MaintainCompanionPacketPolicyUseCase(CompanionPacketPolicySource { if (available) slow else null }, repository, testScheduler.timeSource)
        backgroundScope.launch {
            useCase.execute(repository.activeRegistration.value!!, offline,
                CompanionPacketPolicySink { applied += it; PacketPolicyUpdateResult.APPLIED })
        }
        advanceTimeBy(4_999); runCurrent()
        assertTrue(applied.isEmpty())
        advanceTimeBy(1); runCurrent()
        assertEquals(listOf(PacketConditionConfiguration.Disabled), applied)
        available = true
        advanceTimeBy(250); runCurrent()
        assertEquals(listOf(PacketConditionConfiguration.Disabled, slow), applied)
    }

    @Test fun `registration change rejects an in-flight old policy and releases its old shaping`() = runTest {
        val repository = Repository()
        val original = repository.activeRegistration.value!!
        val applied = mutableListOf<PacketConditionConfiguration>()
        val useCase = MaintainCompanionPacketPolicyUseCase(CompanionPacketPolicySource { delay(500); slow }, repository, testScheduler.timeSource)
        val result = async {
            useCase.execute(original, offline, CompanionPacketPolicySink { applied += it; PacketPolicyUpdateResult.APPLIED })
        }
        runCurrent()
        repository.activeRegistration.value = original.copy(deviceId = RegisteredDeviceId("different-device"))
        advanceTimeBy(500); runCurrent()
        assertEquals(PacketPolicyMaintenanceResult.REGISTRATION_CHANGED, result.await())
        assertEquals(listOf(PacketConditionConfiguration.Disabled), applied)
    }

    @Test fun `endpoint refresh ignores the stale response and fetches using the new trusted endpoint`() = runTest {
        val repository = Repository()
        val original = repository.activeRegistration.value!!
        val applied = mutableListOf<PacketConditionConfiguration>()
        var reads = 0
        val refreshed = original.copy(controlEndpoint = original.controlEndpoint.copy(port = original.controlEndpoint.port + 1))
        val useCase = MaintainCompanionPacketPolicyUseCase(CompanionPacketPolicySource {
            reads++
            if (reads == 1) { delay(500); offline } else { assertEquals(refreshed, it); slow }
        }, repository, testScheduler.timeSource)
        backgroundScope.launch { useCase.execute(original, PacketConditionConfiguration.Disabled,
            CompanionPacketPolicySink { applied += it; PacketPolicyUpdateResult.APPLIED }) }
        runCurrent()
        repository.activeRegistration.value = refreshed
        advanceTimeBy(500); runCurrent()
        assertEquals(listOf(slow), applied)
    }

    @Test fun `slow reads are bounded and native cancellation is never swallowed`() = runTest {
        val repository = Repository()
        var inFlight = 0
        var maximumInFlight = 0
        val useCase = MaintainCompanionPacketPolicyUseCase(CompanionPacketPolicySource {
            inFlight++; maximumInFlight = maxOf(maximumInFlight, inFlight)
            try { awaitCancellation() } finally { inFlight-- }
        }, repository, testScheduler.timeSource)
        val job = backgroundScope.launch { useCase.execute(repository.activeRegistration.value!!,
            PacketConditionConfiguration.Disabled, CompanionPacketPolicySink { fail("No policy should be applied") }) }
        advanceTimeBy(2_000); runCurrent()
        assertEquals(1, maximumInFlight)
        job.cancelAndJoin()
        assertEquals(0, inFlight)
    }

    @Test fun `inactive and unsupported native peers stop updates without retry loops`() = runTest {
        for (ack in listOf(PacketPolicyUpdateResult.INACTIVE, PacketPolicyUpdateResult.UNSUPPORTED)) {
            val repository = Repository()
            var reads = 0
            val useCase = MaintainCompanionPacketPolicyUseCase(CompanionPacketPolicySource { reads++; slow }, repository, testScheduler.timeSource)
            val result = useCase.execute(repository.activeRegistration.value!!, PacketConditionConfiguration.Disabled, CompanionPacketPolicySink { ack })
            assertEquals(if (ack == PacketPolicyUpdateResult.INACTIVE) PacketPolicyMaintenanceResult.INACTIVE else PacketPolicyMaintenanceResult.UNSUPPORTED, result)
            assertEquals(1, reads)
        }
    }

    private class Repository : CompanionRegistrationRepository {
        override val activeRegistration = MutableStateFlow<CompanionRegistration?>(CompanionUseCasesTest.registration())
        override val registrations = MutableStateFlow(listOf(activeRegistration.value!!))
        override suspend fun upsert(registration: CompanionRegistration, makeActive: Boolean) { if (makeActive) activeRegistration.value = registration }
        override suspend fun setActive(desktopId: CompanionDesktopId?): Boolean = false
        override suspend fun remove(desktopId: CompanionDesktopId): CompanionRegistration? = null
        override suspend fun migrateIdentity(previousDesktopId: CompanionDesktopId, registration: CompanionRegistration, makeActive: Boolean): Boolean = false
    }

    private companion object {
        val slow = PacketConditionConfiguration(enabled = true, downloadBitsPerSecond = 100_000)
        val offline = PacketConditionConfiguration(enabled = true, lossPercent = 100)
    }
}
