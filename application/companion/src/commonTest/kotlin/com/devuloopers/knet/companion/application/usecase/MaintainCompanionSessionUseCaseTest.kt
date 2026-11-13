package com.devuloopers.knet.companion.application.usecase

import com.devuloopers.knet.companion.application.contract.*
import com.devuloopers.knet.companion.model.*
import com.devuloopers.knet.identity.RegisteredDeviceId
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

/** Virtual-time qualification of VPN lifecycle ownership, policy safety leases and reconnect fencing. */
@OptIn(ExperimentalCoroutinesApi::class)
class MaintainCompanionSessionUseCaseTest {
    @Test fun `snapshot acknowledgement reflects native result and duplicates do not reapply`() = runTest {
        for (result in PacketPolicyUpdateResult.entries) {
            val fixture = Fixture()
            fixture.result = result
            val job = backgroundScope.launch { fixture.run(testScheduler) }
            runCurrent()
            val first = fixture.connections.single()
            first.configuration(1)
            runCurrent()
            assertEquals(result.name, first.sent.first { it.kind == CompanionSessionMessageKind.POLICY_ACK }.acknowledgement?.name)
            if (result == PacketPolicyUpdateResult.APPLIED) {
                first.configuration(1); runCurrent()
                assertEquals(1, fixture.applied.count { it == slow })
            }
            if (result == PacketPolicyUpdateResult.UNSUPPORTED) {
                advanceTimeBy(16_000); runCurrent()
                assertTrue(job.isActive, "Presence survives unavailable packet shaping")
                assertEquals(1, fixture.connections.size)
                assertTrue(first.sent.any { it.kind == CompanionSessionMessageKind.HEARTBEAT })
            }
            if (result == PacketPolicyUpdateResult.INACTIVE) assertFalse(job.isActive)
            job.cancelAndJoin()
            assertEquals(PacketConditionConfiguration.Disabled, fixture.applied.last())
        }
    }

    @Test fun `lease expiry releases offline shaping and forces a fresh snapshot after reconnect`() = runTest {
        val fixture = Fixture()
        val job = backgroundScope.launch { fixture.run(testScheduler) }
        runCurrent()
        val first = fixture.connections.single()
        first.configuration(1); runCurrent()
        advanceTimeBy(4_999); runCurrent()
        assertFalse(first.closed)
        advanceTimeBy(1); runCurrent()
        assertTrue(first.closed)
        assertEquals(PacketConditionConfiguration.Disabled, fixture.applied.last())
        advanceTimeBy(1_000); runCurrent()
        val second = fixture.connections.last()
        assertNotSame(first, second)
        second.configuration(1); runCurrent()
        assertEquals(slow, fixture.applied.last(), "New channel revision one is applied again")
        job.cancelAndJoin()
    }

    @Test fun `only matching renewal extends lease and heartbeat stops on cancellation`() = runTest {
        val fixture = Fixture()
        val job = backgroundScope.launch { fixture.run(testScheduler) }
        runCurrent()
        val connection = fixture.connections.single()
        connection.configuration(1); runCurrent()
        repeat(8) {
            advanceTimeBy(2_000)
            connection.frames.send(CompanionSessionMessage(CompanionSessionMessageKind.POLICY_RENEWAL, "run", revision = 1))
            runCurrent()
        }
        assertFalse(connection.closed)
        assertTrue(connection.sent.any { it.kind == CompanionSessionMessageKind.HEARTBEAT })
        job.cancelAndJoin()
        assertTrue(connection.closed)
        assertEquals(CompanionSessionMessageKind.STOP, connection.sent.last().kind)
        val count = connection.sent.size
        advanceTimeBy(30_000); runCurrent()
        assertEquals(count, connection.sent.size)
    }

    @Test fun `switching identity closes silently waiting socket immediately and ignores stale policy`() = runTest {
        val fixture = Fixture()
        val job = backgroundScope.launch { fixture.run(testScheduler) }
        runCurrent()
        val first = fixture.connections.single()
        fixture.repository.activeRegistration.value = fixture.registration.copy(deviceId = RegisteredDeviceId("other"))
        runCurrent()
        assertTrue(first.closed)
        advanceTimeBy(1_000); runCurrent()
        assertFalse(job.isActive)
        assertEquals(listOf(PacketConditionConfiguration.Disabled), fixture.applied)
        assertEquals(1, fixture.connections.size)
    }

    @Test fun `authorization rejection stops whereas network failure retries with bounded backoff`() = runTest {
        val fixture = Fixture()
        fixture.failure = CompanionSessionFailure.UNAVAILABLE
        val job = backgroundScope.launch { fixture.run(testScheduler) }
        advanceTimeBy(30_000); runCurrent()
        assertTrue(fixture.attempts in 4..12, "Reconnects cannot spin or grow unbounded")
        fixture.failure = CompanionSessionFailure.UNAUTHORIZED
        advanceTimeBy(6_000); runCurrent()
        assertFalse(job.isActive)
        val attempts = fixture.attempts
        advanceTimeBy(60_000); runCurrent()
        assertEquals(attempts, fixture.attempts)
    }

    @Test fun `unsupported peer uses bounded authenticated HTTPS recovery and closes session owner`() = runTest {
        val fixture = Fixture()
        fixture.failure = CompanionSessionFailure.UNSUPPORTED
        val job = backgroundScope.launch { fixture.run(testScheduler) }
        runCurrent()
        assertEquals(1, fixture.polls)
        assertEquals(1, fixture.attempts)
        advanceTimeBy(1_250); runCurrent()
        assertTrue(fixture.polls >= 2)
        job.cancelAndJoin()
        val polls = fixture.polls
        advanceTimeBy(5_000); runCurrent()
        assertEquals(polls, fixture.polls)
    }

    @Test fun `revocation and failed native apply never acknowledge a successful policy`() = runTest {
        val fixture = Fixture()
        fixture.throwApply = true
        val job = backgroundScope.launch { fixture.run(testScheduler) }
        runCurrent()
        val connection = fixture.connections.single()
        connection.configuration(1); runCurrent()
        assertEquals(CompanionPolicyAcknowledgement.FAILED, connection.sent.last().acknowledgement)
        connection.frames.send(CompanionSessionMessage(CompanionSessionMessageKind.REVOKED, "run"))
        runCurrent()
        assertFalse(job.isActive)
        assertTrue(connection.closed)
    }

    private class Fixture {
        val registration = CompanionUseCasesTest.registration()
        val repository = Repository(registration)
        val connections = mutableListOf<Connection>()
        val applied = mutableListOf<PacketConditionConfiguration>()
        var attempts = 0
        var polls = 0
        var failure: CompanionSessionFailure? = null
        var result = PacketPolicyUpdateResult.APPLIED
        var throwApply = false
        suspend fun run(scheduler: TestCoroutineScheduler) {
            val credentials = object : CompanionCredentialStore {
                override suspend fun write(reference: CompanionCredentialReference, credential: String) = Unit
                override suspend fun read(reference: CompanionCredentialReference) = "credential"
                override suspend fun remove(reference: CompanionCredentialReference) = Unit
            }
            val fallback = MaintainCompanionPacketPolicyUseCase(CompanionPacketPolicySource { polls++; slow }, repository, scheduler.timeSource)
            MaintainCompanionSessionUseCase(CompanionSessionTransport { _, _ ->
                attempts++
                failure?.let { throw CompanionSessionException(it) }
                Connection().also { connections += it }
            }, credentials, repository, fallback, scheduler.timeSource).execute(registration, "run", PacketConditionConfiguration.Disabled,
                CompanionPacketPolicySink { policy -> applied += policy; if (throwApply) error("native failure"); result })
        }
    }
    private class Connection : CompanionSessionConnection {
        val frames = Channel<CompanionSessionMessage>(16)
        val sent = mutableListOf<CompanionSessionMessage>()
        var closed = false
        init { frames.trySend(CompanionSessionMessage(CompanionSessionMessageKind.WELCOME, "run")) }
        suspend fun configuration(revision: Long) { frames.send(CompanionSessionMessage(CompanionSessionMessageKind.CONFIGURATION, "run", revision = revision, configuration = slow)) }
        override suspend fun send(message: CompanionSessionMessage) { sent += message }
        override suspend fun receive() = frames.receive()
        override suspend fun close() { closed = true; frames.close() }
    }
    private class Repository(registration: CompanionRegistration) : CompanionRegistrationRepository {
        override val activeRegistration = MutableStateFlow<CompanionRegistration?>(registration)
        override val registrations = MutableStateFlow(listOf(registration))
        override suspend fun upsert(registration: CompanionRegistration, makeActive: Boolean) { if (makeActive) activeRegistration.value = registration }
        override suspend fun setActive(desktopId: CompanionDesktopId?): Boolean = false
        override suspend fun remove(desktopId: CompanionDesktopId): CompanionRegistration? = null
        override suspend fun migrateIdentity(previousDesktopId: CompanionDesktopId, registration: CompanionRegistration, makeActive: Boolean): Boolean = false
    }
    private companion object { val slow = PacketConditionConfiguration(enabled = true, downloadBitsPerSecond = 100_000) }
}
