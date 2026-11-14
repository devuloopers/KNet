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
    @Test fun `socket local cancellation reconnects without cancelling the VPN owner`() = runTest {
        val fixture = Fixture()
        val job = backgroundScope.launch { fixture.run(testScheduler) }
        runCurrent()
        val first = fixture.connections.single()
        first.frames.cancel(CancellationException("Remote websocket stopped"))
        runCurrent()
        assertTrue(job.isActive, "Only cancellation of the VPN owner may stop recovery")
        advanceTimeBy(1_000); runCurrent()
        assertEquals(2, fixture.connections.size)
        assertTrue(first.closed)
        assertEquals(CompanionSessionMessageKind.START, fixture.connections.last().sent.first().kind)
        job.cancelAndJoin()
    }

    @Test fun `silent control peer reconnects even when packet shaping is unsupported`() = runTest {
        val fixture = Fixture().apply { result = PacketPolicyUpdateResult.UNSUPPORTED }
        val job = backgroundScope.launch { fixture.run(testScheduler) }
        runCurrent()
        val first = fixture.connections.single()
        first.configuration(1); runCurrent()
        advanceTimeBy(12_000); runCurrent()
        assertTrue(job.isActive)
        assertTrue(first.closed, "Control health must not depend on an applied packet policy")
        assertTrue(fixture.connections.size >= 2)
        job.cancelAndJoin()
    }

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
                repeat(8) {
                    advanceTimeBy(2_000)
                    first.frames.send(CompanionSessionMessage(CompanionSessionMessageKind.POLICY_RENEWAL, "run", revision = 1))
                    runCurrent()
                }
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

    @Test fun `repeated desktop restarts preserve VPN identity and report confirmed recovery`() = runTest {
        val fixture = Fixture().apply { autoConfiguration = true }
        val job = backgroundScope.launch { fixture.run(testScheduler) }
        runCurrent()
        repeat(4) {
            assertEquals(CompanionSessionPhase.CONNECTED, fixture.maintain.state.value?.phase)
            fixture.connections.last().frames.cancel(CancellationException("Desktop restart"))
            runCurrent()
            assertEquals(CompanionSessionPhase.RECONNECTING, fixture.maintain.state.value?.phase)
            advanceTimeBy(5_000); runCurrent()
        }
        assertEquals(5, fixture.connections.size)
        assertEquals(setOf("run"), fixture.connections.flatMap { it.sent }.map { it.runId }.toSet())
        job.cancelAndJoin()
        assertNull(fixture.maintain.state.value)
        val attempts = fixture.attempts
        advanceTimeBy(60_000); runCurrent()
        assertEquals(attempts, fixture.attempts)
    }

    @Test fun `connection local cancellation during handshake and send also recovers`() = runTest {
        for (stage in listOf("connect", "send")) {
            val fixture = Fixture().apply { cancelOnceAt = stage }
            val job = backgroundScope.launch { fixture.run(testScheduler) }
            runCurrent()
            assertTrue(job.isActive)
            advanceTimeBy(1_000); runCurrent()
            assertEquals(CompanionSessionPhase.CONNECTED, fixture.maintain.state.value?.phase)
            assertEquals(2, fixture.attempts)
            job.cancelAndJoin()
        }
    }

    @Test fun `long outage keeps bounded retries and recovers without manual VPN reconnect`() = runTest {
        val fixture = Fixture().apply { failure = CompanionSessionFailure.UNAVAILABLE }
        val job = backgroundScope.launch { fixture.run(testScheduler) }
        advanceTimeBy(600_000); runCurrent()
        assertTrue(job.isActive)
        assertTrue(fixture.attempts in 100..160)
        fixture.failure = null
        advanceTimeBy(5_000); runCurrent()
        assertEquals(CompanionSessionPhase.CONNECTED, fixture.maintain.state.value?.phase)
        job.cancelAndJoin()
    }

    @Test fun `credential rotation during rejected handshake retries with the current credential`() = runTest {
        val fixture = Fixture().apply { rotateOnConnect = true }
        val job = backgroundScope.launch { fixture.run(testScheduler) }
        runCurrent()
        assertTrue(job.isActive)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(listOf("credential", "rotated"), fixture.usedCredentials)
        assertEquals(CompanionSessionPhase.CONNECTED, fixture.maintain.state.value?.phase)
        job.cancelAndJoin()
    }

    @Test fun `cancellation during connection attempt cannot schedule another retry`() = runTest {
        val fixture = Fixture().apply { stallConnect = true }
        val job = backgroundScope.launch { fixture.run(testScheduler) }
        runCurrent()
        job.cancelAndJoin()
        advanceTimeBy(60_000); runCurrent()
        assertEquals(1, fixture.attempts)
        assertNull(fixture.maintain.state.value)
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
        var cancelOnceAt: String? = null
        var rotateOnConnect = false
        var stallConnect = false
        var autoConfiguration = false
        var credential = "credential"
        val usedCredentials = mutableListOf<String>()
        lateinit var maintain: MaintainCompanionSessionUseCase
        suspend fun run(scheduler: TestCoroutineScheduler) {
            val credentials = object : CompanionCredentialStore {
                override suspend fun write(reference: CompanionCredentialReference, credential: String) = Unit
                override suspend fun read(reference: CompanionCredentialReference) = credential
                override suspend fun remove(reference: CompanionCredentialReference) = Unit
            }
            val fallback = MaintainCompanionPacketPolicyUseCase(CompanionPacketPolicySource { polls++; slow }, repository, scheduler.timeSource)
            maintain = MaintainCompanionSessionUseCase(CompanionSessionTransport { _, attemptedCredential ->
                attempts++
                usedCredentials += attemptedCredential
                if (stallConnect) awaitCancellation()
                if (cancelOnceAt == "connect") { cancelOnceAt = null; throw CancellationException("Socket handshake cancelled") }
                if (rotateOnConnect) {
                    rotateOnConnect = false; credential = "rotated"
                    throw CompanionSessionException(CompanionSessionFailure.UNAUTHORIZED)
                }
                failure?.let { throw CompanionSessionException(it) }
                Connection(cancelSend = cancelOnceAt == "send", autoConfiguration = autoConfiguration).also { connections += it; cancelOnceAt = null }
            }, credentials, repository, fallback, scheduler.timeSource)
            maintain.execute(registration, "run", PacketConditionConfiguration.Disabled,
                CompanionPacketPolicySink { policy -> applied += policy; if (throwApply) error("native failure"); result })
        }
    }
    private class Connection(var cancelSend: Boolean = false, autoConfiguration: Boolean = false) : CompanionSessionConnection {
        val frames = Channel<CompanionSessionMessage>(16)
        val sent = mutableListOf<CompanionSessionMessage>()
        var closed = false
        init {
            frames.trySend(CompanionSessionMessage(CompanionSessionMessageKind.WELCOME, "run"))
            if (autoConfiguration) frames.trySend(CompanionSessionMessage(CompanionSessionMessageKind.CONFIGURATION,
                "run", revision = 1, configuration = PacketConditionConfiguration.Disabled))
        }
        suspend fun configuration(revision: Long) { frames.send(CompanionSessionMessage(CompanionSessionMessageKind.CONFIGURATION, "run", revision = revision, configuration = slow)) }
        override suspend fun send(message: CompanionSessionMessage) {
            if (cancelSend) { cancelSend = false; throw CancellationException("Socket send cancelled") }
            sent += message
        }
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
