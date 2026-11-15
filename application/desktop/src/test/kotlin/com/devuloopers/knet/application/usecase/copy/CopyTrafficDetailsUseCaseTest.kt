package com.devuloopers.knet.application.usecase.copy

import com.devuloopers.knet.application.contract.export.CaptureExportSource
import com.devuloopers.knet.application.contract.traffic.*
import com.devuloopers.knet.domain.graphql.*
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.traffic.id.*
import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.traffic.model.body.*
import com.devuloopers.knet.traffic.model.http.*
import com.devuloopers.knet.traffic.model.message.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** Copy resolves exact canonical records and produces bounded readable evidence rather than replaying data. */
class CopyTrafficDetailsUseCaseTest {
    @Test fun `single details contain labelled sections indented JSON and no empty body section`() = runTest {
        val f = TrafficCopyFixture(); f.row(responseBody = f.body("{\"ok\":true}".encodeToByteArray()))
        val text = f.useCase.execute(f.request("one"))
        assertContains(text, "TRAFFIC DETAILS - 1 ITEM\nSource: Other traffic")
        assertContains(text, "1. GET /users\nHost: api.test:443\nURL: https://api.test:443/users?page=1")
        assertContains(text, "Status: 200 OK\nDuration: 438 ms")
        assertContains(text, "Request\n  Params:\n    page: 1\n\n  Headers:\n    Accept: application/json")
        assertContains(text, "Response\n  Headers:\n    Content-Type: application/json\n\n  Body:\n    {\n      \"ok\": true\n    }")
        assertEquals(1, Regex("  Body:").findAll(text).count()); assertEquals(f.leases, f.released)
    }
    @Test fun `multiple items use canonical newest first order only requested IDs and clear separators`() = runTest {
        val f = TrafficCopyFixture(); f.row("old", 1); f.row("new", 3); f.row("unselected", 9)
        val text = f.useCase.execute(f.request("old", "new"))
        assertContains(text, "2 ITEMS"); assertEquals(1, Regex("----------------------------------------").findAll(text).count())
        assertTrue(text.indexOf("00:00:00.003Z") < text.indexOf("00:00:00.001Z")); assertFalse(text.contains("00:00:00.009Z"))
        assertEquals(2, Regex("Host: api.test:443").findAll(text).count())
    }
    @Test fun `duplicate query and header pairs decoding Unicode literal question marks and fragments remain correct`() = runTest {
        val f = TrafficCopyFixture(); val row = f.row(path = "/users?tag=a&tag=b&name=Zo%C3%AB+Smith&literal=a?b&plus=%2B#fragment")
        f.rows[row.id] = row.copy(request = row.request.copy(head = row.request.head.copy(headers = listOf(
            HeaderField(HeaderName("X-Value"), "one"), HeaderField(HeaderName("X-Value"), "two\r\nInjected: value")))))
        val text = f.useCase.execute(f.request("one"))
        assertContains(text, "tag: a\n    tag: b"); assertContains(text, "name: Zoë Smith"); assertContains(text, "literal: a?b"); assertContains(text, "plus: +")
        assertContains(text, "X-Value: one\n    X-Value: two\\r\\nInjected: value")
        assertFalse(text.contains("    plus: +#fragment"))
    }
    @Test fun `missing records are explicit and removed selection never expands while all missing fails`() = runTest {
        val f = TrafficCopyFixture(); f.row()
        val text = f.useCase.execute(f.request("missing", "one")); assertContains(text, "[Cleared before copying]")
        assertFailsWith<IllegalStateException> { f.useCase.execute(f.request("missing")) }
        assertEquals(f.leases, f.released)
    }
    @Test fun `foreign source rejects before any body access and typed opaque identities cannot alias HTTP`() = runTest {
        val f = TrafficCopyFixture(); f.row(responseBody = f.body("private".encodeToByteArray()))
        assertFailsWith<IllegalArgumentException> { f.useCase.execute(f.request("one").copy(source = TrafficSource.Device(RegisteredDeviceId("foreign")))) }
        assertTrue(f.reads.isEmpty()); assertEquals(f.leases, f.released)
        val text = f.useCase.execute(CaptureExportSource.Selection(listOf(TrafficRecordId.Opaque(OpaqueFlowId("one")), TrafficRecordId.Exchange(ExchangeId("one"))), TrafficSource.Other))
        assertContains(text, "Unavailable item"); assertEquals(1, Regex("Host: api.test").findAll(text).count())
    }
    @Test fun `missing truncated binary and unavailable bodies have explicit evidence labels and bounded reads`() = runTest {
        val f = TrafficCopyFixture()
        f.row(responseBody = f.body(ByteArray(100_000) { 120 }, BodyCaptureOutcome.Truncated(100_000), 200_000))
        assertContains(f.useCase.execute(f.request("one")), "Truncated: showing 65536 of 200000")
        assertEquals(65_536, f.maximumBody)
        f.bodyFailure = true; assertContains(f.useCase.execute(f.request("one")), "captured body could not be read")
        f.bodyFailure = false; f.row(responseBody = f.body(byteArrayOf(0, 1, 2, 3)))
        assertContains(f.useCase.execute(f.request("one")), "Binary payload")
        f.row(responseBody = MessageBodyRef.Unavailable(BodyCaptureOutcome.Skipped(BodySkipReason.USER_DISABLED)))
        assertContains(f.useCase.execute(f.request("one")), "Unavailable:")
    }
    @Test fun `GraphQL query variables extensions and selector are separated without duplicate encoded parameters`() = runTest {
        val f = TrafficCopyFixture(); f.row(path = "/graphql?query=query+Q%7Bid%7D&variables=%7B%7D&operationName=Q&extra=1")
        f.graphQL = GraphQLHttpDocument(GraphQLHttpSource.URL_PARAMETERS, queryText = "query Q { id }", variablesJson = "{\"ok\":true}", extensionsJson = "{}", suppliedOperationName = "Q")
        val text = f.useCase.execute(f.request("one"))
        assertContains(text, "Operation name:\n    Q"); assertContains(text, "Query:\n    query Q { id }"); assertContains(text, "Variables:\n    {\n      \"ok\": true")
        assertContains(text, "extra: 1"); assertFalse(text.contains("    query: query Q"))
    }
    @Test fun `WebSocket SSE gRPC messages are bounded labelled by direction and trailers remain visible`() = runTest {
        val f = TrafficCopyFixture(); val row = f.row()
        f.rows[row.id] = row.copy(response = row.response!!.copy(trailers = listOf(HeaderField(HeaderName("grpc-status"), "0"))))
        f.framed = ProtocolMessagePage(listOf(MessageProtocolId.WEBSOCKET, MessageProtocolId.GRPC, MessageProtocolId.SSE).mapIndexed { index, protocol ->
            ProtocolMessageSnapshot(ProtocolMessageId("message-$index"), ConnectionId("connection"), row.id, null, protocol,
                if (protocol == MessageProtocolId.GRPC) ProtocolMessageKind.DATA else ProtocolMessageKind.TEXT,
                TrafficDirection.SERVER_TO_CLIENT, index.toLong(), 1, null, 11, false, null, f.body("{\"ok\":true}".encodeToByteArray()), ProtocolMessageState.COMPLETE)
        }, ProtocolMessagePageCursor("next"), 100)
        val text = f.useCase.execute(f.request("one"))
        assertContains(text, "grpc-status: 0"); assertContains(text, "Messages (3 of 100)")
        for (name in listOf("websocket", "grpc", "sse")) assertContains(text, name)
        assertContains(text, "Binary payload"); assertContains(text, "additional captured messages are not included")
    }
    @Test fun `opaque connections copy observed rejection close evidence bytes and no fake response body`() = runTest {
        val f = TrafficCopyFixture(); val id = OpaqueFlowId("tls")
        f.flows[id] = OpaqueFlowSnapshot(id, ConnectionId("conn"), TrafficEndpoint("play.test", 443), "play.test", OpaqueTransportProtocol.TCP,
            OpaqueSecurityProtocol.TLS, null, null, startedAtEpochMillis = 1, completedAtEpochMillis = 2, uploadedBytes = 100, downloadedBytes = 50,
            state = OpaqueFlowState.COMPLETED, terminalOutcome = ExchangeTerminalOutcome.Completed,
            closure = OpaqueFlowClosure(OpaqueFlowCloseInitiator.SERVER, OpaqueFlowCloseReason.PEER_EOF, 2))
        val text = f.useCase.execute(CaptureExportSource.Selection(listOf(TrafficRecordId.Opaque(id)), TrafficSource.Other))
        assertContains(text, "TLS connection"); assertContains(text, "Closed by:\n    Server"); assertContains(text, "Close reason:\n    Peer eof")
        assertContains(text, "HTTP headers and bodies are unavailable"); assertFalse(text.contains("Response\n")); assertTrue(f.reads.isEmpty())
    }
    @Test fun `in flight empty bodies are labelled without claiming a completed response`() = runTest {
        val f = TrafficCopyFixture(); val row = f.row()
        f.rows[row.id] = row.copy(state = ExchangeState.REQUEST_STREAMING, response = null, timings = ExchangeTimings(), terminalOutcome = null)
        val pending = f.useCase.execute(f.request("one"))
        assertContains(pending, "State: Request streaming")
        assertContains(pending, "[Request body not finalized at copy time]")
        assertContains(pending, "[No response captured; Request streaming]")
        assertFalse(pending.contains("Duration:")); assertFalse(pending.contains("200 OK"))
        f.rows[row.id] = row.copy(state = ExchangeState.RESPONSE_STREAMING, terminalOutcome = null)
        assertContains(f.useCase.execute(f.request("one")), "[Response body not finalized at copy time]")
    }
    @Test fun `message pages enforce parent ownership and bounds before accessing private payloads`() = runTest {
        val f = TrafficCopyFixture(); val row = f.row()
        val privateBody = f.body("private-message".encodeToByteArray())
        val message = ProtocolMessageSnapshot(ProtocolMessageId("foreign"), ConnectionId("other"), ExchangeId("other"), null,
            MessageProtocolId.WEBSOCKET, ProtocolMessageKind.TEXT, TrafficDirection.SERVER_TO_CLIENT,
            1, 1, null, 15, false, null, privateBody, ProtocolMessageState.COMPLETE)
        f.framed = ProtocolMessagePage(List(40) { message.copy(id = ProtocolMessageId("foreign-$it")) }, null, 40)
        val text = f.useCase.execute(f.request("one"))
        assertContains(text, "Messages (32 of 40)")
        assertEquals(32, Regex("message parent does not match").findAll(text).count())
        assertContains(text, "additional captured messages are not included")
        assertTrue(f.reads.isEmpty()); assertFalse(text.contains("private-message"))
    }
    @Test fun `compressed JSON decodes boundedly and corrupt encoding remains explicit`() = runTest {
        val f = TrafficCopyFixture()
        val compressed = java.io.ByteArrayOutputStream().also { output ->
            java.util.zip.GZIPOutputStream(output).use { it.write("{\"ok\":true}".encodeToByteArray()) }
        }.toByteArray()
        val row = f.row(responseBody = f.body(compressed))
        val response = assertNotNull(row.response)
        fun encoded(body: MessageBodyRef) { f.rows[row.id] = row.copy(response = response.copy(
            head = response.head.copy(headers = response.head.headers + HeaderField(HeaderName("Content-Encoding"), "gzip")), body = body)) }
        encoded(response.body)
        assertContains(f.useCase.execute(f.request("one")), "Body:\n    {\n      \"ok\": true\n    }")
        encoded(f.body(byteArrayOf(1, 2, 3)))
        assertContains(f.useCase.execute(f.request("one")), "Unable to decode payload")
        val bomb = java.io.ByteArrayOutputStream().also { output ->
            java.util.zip.GZIPOutputStream(output).use { it.write(ByteArray(200_000) { 120 }) }
        }.toByteArray()
        encoded(f.body(bomb))
        assertContains(f.useCase.execute(f.request("one")), "Unable to decode payload")
        assertEquals(f.leases, f.released)
    }
    @Test fun `oversized selections fail boundedly and cancellation always releases body lease`() = runTest {
        val f = TrafficCopyFixture(); val row = f.row()
        f.rows[row.id] = row.copy(request = row.request.copy(head = row.request.head.copy(headers = listOf(HeaderField(HeaderName("Huge"), "x".repeat(4_194_304))))))
        assertFailsWith<IllegalArgumentException> { f.useCase.execute(f.request("one")) }; assertTrue(f.reads.isEmpty())
        f.beforeRead = { throw CancellationException("cancelled") }
        assertFailsWith<CancellationException> { f.useCase.execute(f.request("one")) }; assertEquals(f.leases, f.released)
    }
}
