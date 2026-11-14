package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.application.contract.traffic.*
import com.devuloopers.knet.data.desktop.traffic.repository.DesktopTrafficQueryAdapter
import com.devuloopers.knet.domain.policy.PolicyWorkspace
import com.devuloopers.knet.engine.proxy.capture.*
import com.devuloopers.knet.engine.session.FileBodyStore
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.storage.database.DatabaseFactory
import com.devuloopers.knet.traffic.id.*
import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.traffic.model.http.*
import com.devuloopers.knet.traffic.model.message.*
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.*

/** Real canonical writer/body-store tests for source cuts across every framed capture family. */
class ScopedStreamingCaptureTest {
    @Test fun websocketPartialMessageIsCutWithoutAffectingSibling() = runTest { verifyFrames("websocket", MessageProtocolId.WEBSOCKET) }
    @Test fun graphqlWebsocketPartialMessageIsCutWithoutAffectingSibling() = runTest { verifyFrames("graphql-ws", MessageProtocolId.WEBSOCKET) }
    @Test fun grpcPartialMessageIsCutWithoutAffectingSibling() = runTest { verifyFrames("grpc", MessageProtocolId.GRPC) }
    @Test fun ssePartialRecordIsCutWithoutAffectingSibling() = runTest { verifyFrames("sse", MessageProtocolId.SSE) }

    private suspend fun verifyFrames(label: String, protocol: MessageProtocolId) = fixture { session, sink, reader, dao ->
        val owner = sink.openConnection(metadata("owner"))
        val sibling = sink.openConnection(metadata("sibling"))
        val first = assertNotNull(owner.startExchange(ExchangeId("$label-owner"), request(), 1, streamId = StreamId(1)))
        val second = assertNotNull(sibling.startExchange(ExchangeId("$label-sibling"), request(), 1, streamId = StreamId(3)))
        val firstMessage = assertNotNull(first.startMessage(message("$label-owner-frame", protocol)))
        val accepted = assertNotNull(firstMessage.tryReservePayload(1))
        accepted.writableBytes[0] = 65; assertTrue(accepted.publish(2))
        val reservedBeforeCut = assertNotNull(firstMessage.tryReservePayload(1))
        sink.setAdmission(CaptureAdmissionPolicy(false, mapOf(siblingWorkspace to true)))
        assertFalse(reservedBeforeCut.publish(3), "A reservation cannot append after source pause")
        assertNull(firstMessage.tryReservePayload(1))
        assertNull(first.startMessage(message("$label-after-cut", protocol)))
        firstMessage.complete(100, 4) // A late framer completion must not overwrite cancellation.
        val siblingMessage = assertNotNull(second.startMessage(message("$label-sibling-frame", protocol)))
        val siblingBytes = assertNotNull(siblingMessage.tryReservePayload(1))
        siblingBytes.writableBytes[0] = 66; assertTrue(siblingBytes.publish(5)); siblingMessage.complete(1, 6)
        session.flush()
        val cut = assertNotNull(dao.getDuplexMessage("$label-owner-frame"))
        assertEquals("CANCELLED", cut.state)
        assertEquals("capture_paused", cut.errorCode)
        assertEquals(1L, cut.observedBytes)
        assertEquals("COMPLETE", dao.getDuplexMessage("$label-sibling-frame")?.state)
        assertNull(dao.getDuplexMessage("$label-after-cut"))
        assertEquals("CANCELLED", dao.getExchange("$label-owner")?.state)
        assertNull(dao.getExchange("$label-sibling")?.completedAtEpochMillis)
        assertEquals(1L, reader.query(TrafficPageQuery(limit = 10, source = TrafficSource.Device(RegisteredDeviceId("owner")))).totalCount)
        sink.setAdmission(CaptureAdmissionPolicy(false, mapOf(ownerWorkspace to true, siblingWorkspace to true)))
        val resumed = assertNotNull(owner.startExchange(ExchangeId("$label-resumed"), request(), 7, streamId = StreamId(5)))
        assertNotNull(resumed.startMessage(message("$label-new-stream", protocol)))
        owner.close(); sibling.close(); session.flush()
        assertEquals(2L, reader.query(TrafficPageQuery(limit = 10, source = TrafficSource.Device(RegisteredDeviceId("owner")))).totalCount)
    }

    @Test fun partialHttpBodyIsFinalizedAndOutstandingAllocationIsCancelledAtTheSourceCut() = runTest {
        fixture { session, sink, _, dao ->
            val owner = sink.openConnection(metadata("owner"))
            val exchange = assertNotNull(owner.startExchange(ExchangeId("partial-http"), request(), 1))
            val first = assertNotNull(exchange.tryReserveBody(TrafficDirection.SERVER_TO_CLIENT, null, 1))
            first.writableBytes[0] = 65; assertTrue(first.publish(2))
            val reserved = assertNotNull(exchange.tryReserveBody(TrafficDirection.SERVER_TO_CLIENT, null, 1))
            sink.setAdmission(CaptureAdmissionPolicy(false, mapOf(siblingWorkspace to true)))
            assertFalse(reserved.publish(3))
            assertNull(exchange.tryReserveBody(TrafficDirection.SERVER_TO_CLIENT, null, 1))
            exchange.completeBody(TrafficDirection.SERVER_TO_CLIENT, 200, 4)
            exchange.terminate(ExchangeTerminalOutcome.Completed, ExchangeTimings(), 5)
            session.flush()
            val row = assertNotNull(dao.getExchange("partial-http"))
            assertEquals("CANCELLED", row.state)
            val body = assertNotNull(dao.getBody(assertNotNull(row.responseBodyId)))
            assertEquals(1L, body.storedBytes)
            assertTrue(body.outcome.contains("capture_paused"), body.outcome)
            owner.close()
        }
    }

    @Test fun opaqueTlsCountersAndTerminationStayScopedAndResumeUsesANewRecordingBoundary() = runTest {
        fixture { session, sink, reader, _ ->
            val owner = sink.openConnection(metadata("owner")); val sibling = sink.openConnection(metadata("sibling"))
            val target = ProxyOpaqueFlowCaptureMetadata(TrafficEndpoint("example.test", 443), "example.test",
                ProxyOpaqueTransportProtocol.TCP, ProxyOpaqueSecurityProtocol.TLS, null, occurredAtEpochMillis = 1)
            val first = assertNotNull(owner.startOpaqueFlow(target)); val second = assertNotNull(sibling.startOpaqueFlow(target))
            first.observeBytes(TrafficDirection.CLIENT_TO_SERVER, 10, 2)
            second.observeBytes(TrafficDirection.CLIENT_TO_SERVER, 20, 2)
            sink.setAdmission(CaptureAdmissionPolicy(false, mapOf(siblingWorkspace to true)))
            first.observeBytes(TrafficDirection.CLIENT_TO_SERVER, 1_000, 3)
            second.observeBytes(TrafficDirection.CLIENT_TO_SERVER, 30, 3)
            assertNull(owner.startOpaqueFlow(target))
            session.flush()
            val cut = reader.query(TrafficPageQuery(limit = 10, source = TrafficSource.Device(RegisteredDeviceId("owner")))).opaqueItems.single().flow
            val live = reader.query(TrafficPageQuery(limit = 10, source = TrafficSource.Device(RegisteredDeviceId("sibling")))).opaqueItems.single().flow
            assertEquals(10L, cut.uploadedBytes)
            assertEquals("capture_paused", cut.terminalOutcome?.reason?.code?.value)
            assertEquals(50L, live.uploadedBytes)
            assertNull(live.completedAtEpochMillis)
            owner.close(); sibling.close()
        }
    }

    private suspend fun fixture(block: suspend (StreamingProxyCaptureSession, SwitchableProxyCaptureSink,
        DesktopTrafficQueryAdapter, com.devuloopers.knet.storage.capture.dao.CanonicalCaptureDao) -> Unit) {
        val root = Files.createTempDirectory("knet-scoped-capture-").toFile()
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        val store = FileBodyStore(root.resolve("bodies"))
        val session = CanonicalCaptureSessionFactory(database, store, store).openStreamingProxy(8080)
        try {
            block(session, SwitchableProxyCaptureSink(session, CaptureAdmissionPolicy(false,
                mapOf(ownerWorkspace to true, siblingWorkspace to true))),
                DesktopTrafficQueryAdapter(database.canonicalCaptureDao(), store), database.canonicalCaptureDao())
        } finally { session.close(); database.close(); root.deleteRecursively() }
    }
    private fun metadata(identity: String) = ProxyCaptureConnectionMetadata(IngressContext(IngressKind.LanPairedDevice,
        clientIdentity = ClientIdentity(identity)), TrafficEndpoint("127.0.0.1", 50_000), TrafficEndpoint("127.0.0.1", 8080))
    private fun request() = RequestHead(HttpMethod.GET, RequestTarget.Absolute(HttpScheme.fromToken("https"),
        Authority("example.test", 443), "/stream"), ApplicationProtocol.fromToken("HTTP/2"), headers = emptyList())
    private fun message(id: String, protocol: MessageProtocolId) = ProxyMessageCaptureMetadata(ProtocolMessageId(id),
        StreamId(1), protocol, ProtocolMessageKind.DATA, TrafficDirection.SERVER_TO_CLIENT, 1, 2, false, null, 1)
    private companion object {
        val ownerWorkspace = PolicyWorkspace.Device(RegisteredDeviceId("owner"))
        val siblingWorkspace = PolicyWorkspace.Device(RegisteredDeviceId("sibling"))
    }
}
