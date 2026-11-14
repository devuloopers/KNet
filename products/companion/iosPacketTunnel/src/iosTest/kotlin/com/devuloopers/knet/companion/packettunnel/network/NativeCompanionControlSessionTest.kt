package com.devuloopers.knet.companion.packettunnel.network

import com.devuloopers.knet.companion.application.contract.*
import com.devuloopers.knet.companion.model.*
import com.devuloopers.knet.companion.packettunnel.options.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.uuid.Uuid

/** Exercises the real extension lifecycle owner in the Simulator test executable without NetworkExtension entitlement. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NativeCompanionControlSessionTest {
    @Test fun nativeOwnerAcknowledgesCapabilityAndStopsWithoutLeakingHeartbeat() = runTest {
        val connection = Connection()
        val session = NativeCompanionControlSession(options(), {}, CompanionSessionTransport { _, _ -> connection }, backgroundScope)
        session.start(); session.start(); runCurrent()
        val run = connection.sent.first().runId
        connection.frames.send(CompanionSessionMessage(CompanionSessionMessageKind.CONFIGURATION, run, revision = 1,
            configuration = PacketConditionConfiguration(enabled = true, downloadBitsPerSecond = 100_000)))
        runCurrent()
        assertEquals(CompanionPolicyAcknowledgement.UNSUPPORTED, connection.sent.last().acknowledgement)
        connection.frames.send(CompanionSessionMessage(CompanionSessionMessageKind.CONFIGURATION, run, revision = 2,
            configuration = PacketConditionConfiguration.Disabled))
        runCurrent()
        assertEquals(CompanionPolicyAcknowledgement.APPLIED, connection.sent.last().acknowledgement)
        repeat(8) {
            advanceTimeBy(2_000)
            connection.frames.send(CompanionSessionMessage(CompanionSessionMessageKind.POLICY_RENEWAL, run, revision = 2))
            runCurrent()
        }
        assertTrue(connection.sent.any { it.kind == CompanionSessionMessageKind.HEARTBEAT })
        session.close(); runCurrent()
        assertTrue(connection.closed)
        assertEquals(CompanionSessionMessageKind.STOP, connection.sent.last().kind)
        val count = connection.sent.size
        advanceTimeBy(60_000); runCurrent()
        assertEquals(count, connection.sent.size)
    }
    @Test fun nativeRevocationTerminatesTheOwnerWithoutReconnect() = runTest {
        val connection = Connection()
        var revoked = 0; var attempts = 0
        val session = NativeCompanionControlSession(options(), { revoked++ }, CompanionSessionTransport { _, _ -> attempts++; connection }, backgroundScope)
        session.start(); runCurrent()
        connection.frames.send(CompanionSessionMessage(CompanionSessionMessageKind.REVOKED, connection.sent.first().runId))
        runCurrent()
        assertEquals(1, revoked); assertTrue(connection.closed)
        advanceTimeBy(30_000); runCurrent()
        assertEquals(1, attempts)
        session.close()
    }
    @Test fun nativeSocketCancellationReconnectsButExplicitStopDoesNot() = runTest {
        val connections = mutableListOf<Connection>()
        val reconnecting = mutableListOf<Boolean>()
        val session = NativeCompanionControlSession(options(), {}, CompanionSessionTransport { _, _ ->
            Connection().also { connections += it }
        }, backgroundScope, reconnecting::add)
        session.start(); runCurrent()
        val first = connections.single()
        first.frames.cancel(CancellationException("Desktop stopped")); runCurrent()
        assertEquals(true, reconnecting.last())
        advanceTimeBy(1_000); runCurrent()
        assertEquals(2, connections.size)
        assertEquals(false, reconnecting.last())
        assertEquals(first.sent.first().runId, connections.last().sent.first().runId)
        session.close(); runCurrent()
        advanceTimeBy(60_000); runCurrent()
        assertEquals(2, connections.size)
        assertTrue(connections.all { it.closed })
    }
    @Test fun nativeSilentPeerIsReplacedWithoutPacketShapingSupport() = runTest {
        val connections = mutableListOf<Connection>()
        val session = NativeCompanionControlSession(options(), {}, CompanionSessionTransport { _, _ ->
            Connection().also { connections += it }
        }, backgroundScope)
        session.start(); runCurrent()
        advanceTimeBy(12_000); runCurrent()
        assertEquals(2, connections.size)
        assertTrue(connections.first().closed)
        session.close(); runCurrent()
    }
    @Test fun nativeUnauthorizedPairingDoesNotLoop() = runTest {
        var attempts = 0
        var revoked = 0
        val session = NativeCompanionControlSession(options(), { revoked++ }, CompanionSessionTransport { _, _ ->
            attempts++; throw CompanionSessionException(CompanionSessionFailure.UNAUTHORIZED)
        }, backgroundScope)
        session.start(); runCurrent()
        advanceTimeBy(60_000); runCurrent()
        assertEquals(1, attempts); assertEquals(1, revoked)
        session.close()
    }
    private class Connection : CompanionSessionConnection {
        val frames = Channel<CompanionSessionMessage>(16)
        val sent = mutableListOf<CompanionSessionMessage>()
        var closed = false
        override suspend fun send(message: CompanionSessionMessage) {
            sent += message
            if (message.kind == CompanionSessionMessageKind.START) frames.send(CompanionSessionMessage(CompanionSessionMessageKind.WELCOME, message.runId))
        }
        override suspend fun receive() = frames.receive()
        override suspend fun close() { closed = true; frames.close() }
    }
    private fun options() = TunnelStartOptions(Uuid.parse("87961b5c-08f5-4037-8c58-ca54135d6fe7"), "192.168.1.25", IpAddressFamily.IPV4,
        8080.toUShort(), "Bearer device:abcdefghijklmnop", byteArrayOf(1), "1".repeat(64), "2".repeat(64), "192.168.1.25", 8183.toUShort())
}
