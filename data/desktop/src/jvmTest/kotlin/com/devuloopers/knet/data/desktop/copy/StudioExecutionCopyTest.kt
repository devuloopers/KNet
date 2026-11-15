package com.devuloopers.knet.data.desktop.copy

import com.devuloopers.knet.application.contract.copy.*
import com.devuloopers.knet.application.contract.export.*
import com.devuloopers.knet.application.contract.traffic.*
import com.devuloopers.knet.application.contract.traffic.TrafficCopyPreset.*
import com.devuloopers.knet.application.contract.apistudio.*
import com.devuloopers.knet.application.usecase.traffic.CopyStudioExecutionUseCase
import com.devuloopers.knet.data.desktop.export.DesktopStudioExportRecorder
import com.devuloopers.knet.data.desktop.traffic.DesktopTrafficDetailsFormatting
import com.devuloopers.knet.domain.clientNetwork.executor.*
import com.devuloopers.knet.domain.clientNetwork.model.*
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.engine.sse.integration.apistudio.SseHttpResponseStreamInterpreter
import com.devuloopers.knet.traffic.model.body.MessageBodyRef
import com.devuloopers.knet.traffic.model.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.nio.file.Files
import kotlin.io.encoding.Base64
import kotlin.test.*

/** Real private recorder, immutable bounded file prefixes and shared Traffic formatting; no capture or network. */
class StudioExecutionCopyTest {
    @Test fun `execution copy ignores later draft mutations keeps ordered duplicates and formats original text independently`() = runBlocking { fixture {
        val headers = mutableListOf("Content-Type" to "application/json", "X-Request" to "original")
        val session = recorder.http(StudioExportHttpRequest("https://original.test/graphql?x=1", HttpMethod.POST, headers, OutboundRequestBody.Json("{\"query\":\"query Q { id }\"}")))
        headers.clear(); headers += "X-Request" to "changed"
        val received = mutableListOf("Content-Type" to "application/json", "X-Repeated" to "one", "X-Repeated" to "two", "Set-Cookie" to "a=1; HttpOnly", "Set-Cookie" to "a=2; Secure")
        session.response(head(500, received)); received.clear()
        session.append("{\"ok\":true}".encodeToByteArray()); complete(session)
        assertEquals(RequestKindId.GRAPHQL, session.copyKind)
        assertContains(copy.execute(session.id, FULL), "https://original.test/graphql?x=1")
        assertContains(copy.execute(session.id, REQUEST_HEADERS), "X-Request: original")
        val responseHeaders = copy.execute(session.id, RESPONSE_HEADERS)
        assertContains(responseHeaders, "X-Repeated: one\nX-Repeated: two")
        assertEquals("a=1; HttpOnly\na=2; Secure", copy.execute(session.id, RESPONSE_COOKIES))
        assertEquals("{\n  \"ok\": true\n}", copy.execute(session.id, RESPONSE_BODY))
        assertEquals("{\"ok\":true}", copy.execute(session.id, RESPONSE_ORIGINAL_TEXT))
        assertContains(copy.execute(session.id, URL_RESPONSE_HEADERS_BODY), "500")
        assertFalse(copy.execute(session.id, FULL).contains("X-Request: changed"))
        session.release()
    } }
    @Test fun `all presets read the same response without Traffic and metadata actions do not retain payloads`() = runBlocking { fixture {
        val session = recorder.http(StudioExportHttpRequest("https://copy.test/a?q=1", HttpMethod.POST, emptyList(), OutboundRequestBody.Text("request"))); session.response(head()); session.append("{\"ok\":true}".encodeToByteArray()); complete(session)
        for (preset in TrafficCopyPreset.entries) {
            val text = copy.execute(session.id, preset)
            assertTrue(text.isNotBlank(), preset.name)
            if (preset == STRUCTURED_JSON) assertEquals("apiStudio", Json.parseToJsonElement(text).jsonObject["items"]!!.jsonArray.first().jsonObject["source"]!!.jsonPrimitive.content)
        }
        val metadata = recorder.snapshot(session.id, RESPONSE_HEADERS)
        val ref = metadata.http!!.response!!.body as MessageBodyRef.Available
        assertFailsWith<IllegalStateException> { metadata.readBody(ref.body.id, BodyRange(0, 1)) }
        assertEquals(Base64.encode("{\"ok\":true}".encodeToByteArray()), copy.execute(session.id, RESPONSE_BASE64))
        session.release()
    } }
    @Test fun `copy enables at actual response head including 4xx 5xx but never synthetic transport failures`() = runBlocking { fixture {
        val session = http()
        assertFalse(session.responseReceived.value)
        assertFailsWith<IllegalStateException> { copy.execute(session.id, RESPONSE_BODY) }
        for (status in listOf(200, 400, 404, 500, 502, 503, 504)) {
            session.response(head(status)); assertTrue(session.responseReceived.value)
            assertContains(copy.execute(session.id, FULL), status.toString())
        }
        session.release(); assertFalse(session.responseReceived.value)
        assertFailsWith<IllegalStateException> { copy.execute(session.id, FULL) }
    } }
    @Test fun `snapshot remains owned after release and oversized active prefixes stay explicit`() = runBlocking { fixture {
        val session = http(); session.response(head()); session.append(ByteArray(100_000) { 120 })
        val snapshot = recorder.snapshot(session.id, RESPONSE_ORIGINAL_TEXT)
        val ref = snapshot.http!!.response!!.body as MessageBodyRef.Available
        assertEquals(65_536, snapshot.readBody(ref.body.id, BodyRange(0, 65_536)).size)
        assertContains(copy.execute(session.id, RESPONSE_BODY), "Truncated")
        session.release()
        assertEquals(65_536, snapshot.readBody(ref.body.id, BodyRange(0, 65_536)).size)
        assertFailsWith<IllegalStateException> { recorder.snapshot(session.id, RESPONSE_BODY) }
    } }
    @Test fun `SSE copy uses the real engine parser and does not complete partial records`() = runBlocking { fixture {
        val session = http(); session.response(head(headers = listOf("Content-Type" to "text/event-stream")))
        session.append("event: update\ndata: {\"ok\":true}\n\ndata: unfinished".encodeToByteArray())
        val data = copy.execute(session.id, SSE_DATA)
        assertContains(data, "\"ok\": true"); assertFalse(data.contains("unfinished")); assertContains(data, "still active")
        assertContains(copy.execute(session.id, SSE_EVENTS), "event: update")
        session.release()
    } }
    @Test fun `empty request can produce curl and native sessions retain direction metadata and bounded messages`() = runBlocking { fixture {
        val session = http(); session.response(head()); complete(session)
        assertContains(copy.execute(session.id, CURL), "curl --globoff")
        session.release()
        // Native contract creation is checked by the product integration suite; HTTP snapshots cannot alias that owner.
        val wrong = object : StudioCopySource { override suspend fun snapshot(id: String, preset: TrafficCopyPreset): StudioCopySnapshot = error("not retained") }
        val missing = CopyStudioExecutionUseCase(wrong, DesktopTrafficDetailsFormatting(), ProtocolMessagePresentationRegistry(), HttpResponseStreamInterpreterRegistry())
        assertFailsWith<IllegalStateException> { missing.execute("unknown", FULL) }
    } }

    @Test fun `native messages preserve original execution and direction and disclose the bounded page`() = runBlocking { fixture {
        for (kind in listOf(RequestKindId.WEBSOCKET, RequestKindId.GRAPHQL_WEBSOCKET, RequestKindId.GRPC)) {
            val command = ApiStudioProtocolExecutionCommand(ApiStudioProtocolDocument("doc", "Native", kind, 1, byteArrayOf()))
            val draft = ApiStudioProtocolDraft("doc", "Native", "original.test", 443, true, "service/Call", 1000,
                listOf(ApiStudioProtocolMetadataEntry("X-Request", "original")), emptyList(), null, "wss://original.test/socket")
            val session = recorder.protocol(command, draft)
            assertFalse(session.responseReceived.value)
            session.event(ApiStudioProtocolExecutionEvent.Started("connected", serverResponseReceived = true, responseHeaders = listOf("X-Handshake" to "original")))
            assertTrue(session.responseReceived.value)
            assertEquals("original.test:443", copy.execute(session.id, DESTINATION))
            repeat(40) { index -> session.event(ApiStudioProtocolExecutionEvent.Message(ApiStudioProtocolMessage(index + 1L,
                if (index % 2 == 0) ApiStudioProtocolMessageDirection.OUTBOUND else ApiStudioProtocolMessageDirection.INBOUND,
                if (kind == RequestKindId.GRPC) "application/json" else "text/plain", if (kind == RequestKindId.GRPC) "{\"value\":\"actual-$index\"}" else "different preview",
                if (kind == RequestKindId.GRPC) byteArrayOf(8, index.toByte()) else "actual-$index".encodeToByteArray()))) }
            val client = copy.execute(session.id, WEBSOCKET_CLIENT_MESSAGES)
            assertContains(client, "actual-0"); assertFalse(client.contains("actual-1\n")); assertContains(client, "first 32 of 40")
            assertFalse(client.contains("different preview")); assertFalse(client.contains("HTTP/1.1"))
            session.event(ApiStudioProtocolExecutionEvent.Completed("0", "OK", kind.value, listOf("grpc-status" to "0")))
            assertContains(copy.execute(session.id, FULL), "X-Handshake: original")
            assertContains(copy.execute(session.id, FULL), "grpc-status: 0")
            Json.parseToJsonElement(copy.execute(session.id, STRUCTURED_JSON))
            session.release()
        }
    } }
    @Test fun `semantic encoded curl is refused and failed response prefixes keep their diagnostic`() = runBlocking { fixture {
        val session = recorder.http(StudioExportHttpRequest("https://copy.test/", HttpMethod.POST, listOf("Content-Encoding" to "gzip"), OutboundRequestBody.Text("unencoded")))
        session.response(head()); session.append("prefix".encodeToByteArray())
        session.complete(ExecutionResult(200, "OK", failureReason = NetworkFailureReason.Timeout(1000, "response timeout")))
        assertContains(copy.execute(session.id, CURL), "encoded transport request bytes were not exposed")
        val full = copy.execute(session.id, FULL)
        assertContains(full, "State: Failed"); assertContains(full, "response timeout"); assertContains(full, "Truncated")
        session.release()
    } }
    @Test fun `cancelled responses remain partial and unrepresentable grpc payloads stay binary`() = runBlocking { fixture {
        val http = http(); http.response(head()); http.append(byteArrayOf(1)); http.complete(ExecutionResult(200, "OK", failureReason = NetworkFailureReason.Cancelled))
        assertEquals(com.devuloopers.knet.traffic.model.ExchangeState.CANCELLED, recorder.snapshot(http.id, FULL).http!!.state)
        http.release()
        val command = ApiStudioProtocolExecutionCommand(ApiStudioProtocolDocument("doc", "Native", RequestKindId.GRPC, 1, byteArrayOf()))
        val draft = ApiStudioProtocolDraft("doc", "Native", "original.test", 443, true, "service/Call", 1000, emptyList(), emptyList(), null)
        val session = recorder.protocol(command, draft)
        session.event(ApiStudioProtocolExecutionEvent.Started("connected", serverResponseReceived = true))
        session.event(ApiStudioProtocolExecutionEvent.Message(ApiStudioProtocolMessage(1, ApiStudioProtocolMessageDirection.INBOUND, "application/octet-stream", "preview", byteArrayOf(8, 1))))
        val binary = copy.execute(session.id, GRPC_MESSAGES)
        assertContains(binary, "Binary payload"); assertFalse(binary.contains("preview"))
        session.release()
    } }
    @Test fun `transport encoded request remains representable after a response failure diagnostic`() = runBlocking { fixture {
        val request = recorder.http(StudioExportHttpRequest("https://copy.test/", HttpMethod.POST, listOf("Content-Encoding" to "gzip"), OutboundRequestBody.Text("semantic")))
        request.response(head().copy(sentRequest = HttpSentRequest("https://copy.test/", "POST", listOf("Content-Encoding" to "gzip"), HttpExecutionBytes(byteArrayOf(31, -117, 8, 0)))))
        request.complete(ExecutionResult(200, "OK", failureReason = NetworkFailureReason.Timeout(1000, "response timeout")))
        assertContains(copy.execute(request.id, CURL), "curl --globoff")
        request.release()
    } }
    @Test fun `native failure without repeated headers preserves already received handshake metadata`() = runBlocking { fixture {
        val session = recorder.protocol(ApiStudioProtocolExecutionCommand(ApiStudioProtocolDocument("doc", "Native", RequestKindId.WEBSOCKET, 1, byteArrayOf())),
            ApiStudioProtocolDraft("doc", "Native", "copy.test", 443, true, "", 1000, emptyList(), emptyList(), null, "wss://copy.test/socket"))
        session.event(ApiStudioProtocolExecutionEvent.Started("connected", serverResponseReceived = true, responseHeaders = listOf("X-Handshake" to "retained")))
        session.event(ApiStudioProtocolExecutionEvent.Failed("connection_failed", "transport closed", false))
        val metadata = copy.execute(session.id, WEBSOCKET_HANDSHAKE)
        assertContains(metadata, "X-Handshake: retained"); assertContains(metadata, "connection_failed")
        session.release()
    } }
    @Test fun `stream interpreter is released even if complete response interpretation throws`() = runBlocking { fixture {
        val session = http(); session.response(head(headers = listOf("Content-Type" to "text/event-stream"))); session.append("data: value\n\n".encodeToByteArray()); complete(session)
        var finished = 0
        val interpreter = object : HttpResponseStreamInterpreter {
            override val id = "test-copy-failure"
            override fun supports(head: HttpExecutionResponseHead) = true
            override fun open(head: HttpExecutionResponseHead) = object : HttpResponseStreamInterpreterSession {
                override val protocolLabel = "SSE"
                override val maximumRetainedRecords = 1
                override fun accept(bytes: ByteArray): List<HttpLiveResponseUpdate> = error("decoder failed")
                override fun finish(): List<HttpLiveResponseUpdate> { finished++; return emptyList() }
            }
        }
        val failing = CopyStudioExecutionUseCase(recorder, DesktopTrafficDetailsFormatting(), ProtocolMessagePresentationRegistry(), HttpResponseStreamInterpreterRegistry(listOf(interpreter)))
        assertFailsWith<IllegalStateException> { failing.execute(session.id, SSE_EVENTS) }
        assertEquals(1, finished)
        session.release()
    } }
    private class Fixture {
        val root = Files.createTempDirectory("knet-studio-copy-").toFile()
        val recorder = DesktopStudioExportRecorder(root)
        val copy = CopyStudioExecutionUseCase(recorder, DesktopTrafficDetailsFormatting(), ProtocolMessagePresentationRegistry(), HttpResponseStreamInterpreterRegistry(listOf(SseHttpResponseStreamInterpreter())))
        suspend fun http() = recorder.http(StudioExportHttpRequest("https://copy.test/a?q=1", HttpMethod.GET, emptyList(), OutboundRequestBody.None))
        suspend fun complete(session: StudioExportSession) { session.complete(ExecutionResult(statusCode = 200, statusText = "OK")) }
        fun head(status: Int = 200, headers: List<Pair<String, String>> = listOf("Content-Type" to "application/json")) = HttpExecutionResponseHead(status, "Response", headers.toMap(), emptyMap(), ApplicationProtocol.fromToken("HTTP/2"), headers)
        suspend fun close() { recorder.close(); recorder.awaitCleanup(); root.deleteRecursively() }
    }
    private suspend fun fixture(block: suspend Fixture.() -> Unit) { val f = Fixture(); try { f.block() } finally { f.close() } }
}
