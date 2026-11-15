package com.devuloopers.knet.application.usecase.copy

import com.devuloopers.knet.application.contract.export.CaptureExportSource
import com.devuloopers.knet.application.contract.traffic.*
import com.devuloopers.knet.application.contract.traffic.TrafficCopyPreset.*
import com.devuloopers.knet.domain.graphql.*
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.traffic.id.*
import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.traffic.model.body.*
import com.devuloopers.knet.traffic.model.http.*
import com.devuloopers.knet.traffic.model.message.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import java.nio.file.Files
import kotlin.io.encoding.Base64
import kotlin.test.*

/** Every public preset, exact/mixed selection, bounded reads, protocol filtering and executable shell fidelity. */
class TrafficCopyPresetUseCaseTest {
    @Test fun `all presets resolve exact typed records keep missing members and release leases`() = runTest {
        val f = TrafficCopyFixture(); f.row(responseBody = f.body("{\"ok\":true}".encodeToByteArray())); f.row("unselected", 10, "/private-unselected")
        for (preset in TrafficCopyPreset.entries) {
            val text = f.useCase.execute(f.request("one", "removed"), preset)
            assertTrue(text.isNotBlank(), preset.name); assertFalse(text.contains("private-unselected"), preset.name)
            assertContains(text, "Cleared before copying", message = preset.name); assertEquals(f.leases, f.released)
        }
    }
    @Test fun `metadata presets do not read payloads or framed pages and preserve duplicate destinations`() = runTest {
        val f = TrafficCopyFixture(); f.row(responseBody = f.body("payload".encodeToByteArray())); f.row("two", 2)
        for (preset in listOf(SUMMARY, URL, DESTINATION, PATH_QUERY, QUERY_PARAMETERS, REQUEST_HEADERS, REQUEST_COOKIES, RESPONSE_HEADERS, RESPONSE_COOKIES, CONNECTION, TIMINGS, FAILURE_CLOSURE, GRPC_METADATA, GRPC_METHOD, WEBSOCKET_HANDSHAKE))
            f.useCase.execute(f.request("one", "two"), preset)
        assertTrue(f.reads.isEmpty()); assertEquals(0, f.messageQueries)
        assertEquals("api.test:443\napi.test:443", f.useCase.execute(f.request("one", "two"), DESTINATION))
        assertEquals("https://api.test:443/users?page=1", f.useCase.execute(f.request("one"), URL))
        assertEquals("/users?page=1", f.useCase.execute(f.request("one"), PATH_QUERY))
        assertEquals("page: 1", f.useCase.execute(f.request("one"), QUERY_PARAMETERS))
    }
    @Test fun `request response cookies and repeated headers are separated and combinations include only selected sides`() = runTest {
        val f = TrafficCopyFixture(); val row = f.row(requestBody = f.body("request-secret".encodeToByteArray()), responseBody = f.body("{\"ok\":true}".encodeToByteArray()))
        val originalResponse = assertNotNull(row.response)
        f.rows[row.id] = row.copy(request = row.request.copy(head = row.request.head.copy(headers = listOf(HeaderField(HeaderName("Cookie"), "a=1; b=2"), HeaderField(HeaderName("X-Duplicate"), "1"), HeaderField(HeaderName("X-Duplicate"), "2")))),
            response = originalResponse.copy(head = originalResponse.head.copy(headers = originalResponse.head.headers + listOf(HeaderField(HeaderName("Set-Cookie"), "a=3; HttpOnly"), HeaderField(HeaderName("Set-Cookie"), "b=4")))))
        assertEquals("a=1; b=2", f.useCase.execute(f.request("one"), REQUEST_COOKIES))
        assertEquals("a=3; HttpOnly\nb=4", f.useCase.execute(f.request("one"), RESPONSE_COOKIES))
        assertContains(f.useCase.execute(f.request("one"), REQUEST_HEADERS), "X-Duplicate: 1\nX-Duplicate: 2")
        val response = f.useCase.execute(f.request("one"), URL_RESPONSE_HEADERS_BODY)
        assertContains(response, "URL: https://api.test"); assertContains(response, "200 OK"); assertContains(response, "Set-Cookie"); assertContains(response, "\"ok\": true")
        assertFalse(response.contains("request-secret")); assertFalse(response.contains("X-Duplicate"))
        val request = f.useCase.execute(f.request("one"), URL_REQUEST_BODY)
        assertContains(request, "request-secret"); assertFalse(request.contains("200 OK")); assertFalse(request.contains("Set-Cookie"))
    }
    @Test fun `binary variants preserve exact stored bytes and distinguish direction including empty missing and truncated`() = runTest {
        val f = TrafficCopyFixture(); val bytes = byteArrayOf(0, 1, 10, 127, -1); f.row(requestBody = f.body(bytes), responseBody = f.body(bytes.reversedArray()))
        assertEquals(Base64.encode(bytes), f.useCase.execute(f.request("one"), REQUEST_BASE64))
        assertEquals("00010a7fff", f.useCase.execute(f.request("one"), REQUEST_HEX))
        assertEquals(Base64.encode(bytes.reversedArray()), f.useCase.execute(f.request("one"), RESPONSE_BASE64))
        assertEquals("ff7f0a0100", f.useCase.execute(f.request("one"), RESPONSE_HEX))
        f.row(); assertContains(f.useCase.execute(f.request("one"), RESPONSE_BODY), "Empty body")
        val row = f.row(); f.rows[row.id] = row.copy(response = null)
        assertContains(f.useCase.execute(f.request("one"), RESPONSE_BODY), "No response captured")
        f.row(responseBody = f.body(ByteArray(80_000), BodyCaptureOutcome.Truncated(80_000), 90_000))
        assertContains(f.useCase.execute(f.request("one"), RESPONSE_BASE64), "Truncated: showing 65536 of 90000")
        assertTrue(f.reads.all { it.length <= 65_536 })
    }
    @Test fun `GraphQL components and persisted query provenance do not pretend the hash is the source`() = runTest {
        val f = TrafficCopyFixture(); f.row(path = "/graphql?query=query+Q%7Bid%7D")
        f.graphQL = GraphQLHttpDocument(GraphQLHttpSource.URL_PARAMETERS, queryText = "query Q { id }", variablesJson = "{\"ok\":true}", extensionsJson = "{}", suppliedOperationName = "Q")
        assertEquals("query Q { id }", f.useCase.execute(f.request("one"), GRAPHQL_QUERY))
        assertContains(f.useCase.execute(f.request("one"), GRAPHQL_VARIABLES), "\n  \"ok\": true\n")
        assertEquals("{}", f.useCase.execute(f.request("one"), GRAPHQL_EXTENSIONS))
        assertContains(f.useCase.execute(f.request("one"), GRAPHQL_OPERATION), "Operation name")
        assertContains(f.useCase.execute(f.request("one"), GRAPHQL_REQUEST), "Query source")
        f.graphQL = f.graphQL!!.copy(queryText = "", formattedQuery = "", queryProvenance = GraphQLQueryProvenance.UNAVAILABLE)
        assertContains(f.useCase.execute(f.request("one"), GRAPHQL_QUERY), "Query document unavailable")
        f.graphQL = null; assertContains(f.useCase.execute(f.request("one"), GRAPHQL_QUERY), "No captured GraphQL document")
        assertTrue(f.reads.isEmpty(), "GET GraphQL is extracted without reading an HTTP body")
    }
    @Test fun `framed copies filter protocol direction and foreign parents before reading bodies`() = runTest {
        val f = TrafficCopyFixture(); val row = f.row()
        val client = message(f, row, "client", MessageProtocolId.WEBSOCKET, TrafficDirection.CLIENT_TO_SERVER, "client-value", 1)
        val server = message(f, row, "server", MessageProtocolId.WEBSOCKET, TrafficDirection.SERVER_TO_CLIENT, "server-value", 2)
        val foreign = message(f, row, "foreign", MessageProtocolId.WEBSOCKET, TrafficDirection.CLIENT_TO_SERVER, "foreign-value", 3).copy(exchangeId = ExchangeId("foreign"))
        f.framed = ProtocolMessagePage(listOf(client, server, foreign), ProtocolMessagePageCursor("more"), 90)
        val text = f.useCase.execute(f.request("one"), WEBSOCKET_CLIENT_MESSAGES)
        assertContains(text, "client-value"); assertFalse(text.contains("server-value")); assertFalse(text.contains("foreign-value")); assertContains(text, "parent does not match")
        assertContains(text, "first 3 of 90"); assertEquals(1, f.readIds.size)
        assertContains(f.useCase.execute(f.request("one"), WEBSOCKET_SERVER_MESSAGES), "server-value")
        assertContains(f.useCase.execute(f.request("one"), GRPC_MESSAGES), "No matching captured messages")
    }
    @Test fun `SSE data uses protocol decoder application data and gRPC metadata includes trailers`() = runTest {
        val f = TrafficCopyFixture(); val row = f.row()
        f.presentations = ProtocolMessagePresentationRegistry(listOf(object : ProtocolMessagePayloadDecoder {
            override val decoderId = ProtocolMessagePayloadDecoderId("test-sse"); override val protocolId = MessageProtocolId.SSE; override val priority = 100
            override fun decode(input: ProtocolMessagePayloadInput) = ProtocolMessagePresentation("SSE", "text/event-stream", input.payload.decodeToString(), dataText = "{\"ok\":true}")
        }))
        f.framed = ProtocolMessagePage(listOf(message(f, row, "sse", MessageProtocolId.SSE, TrafficDirection.SERVER_TO_CLIENT, "event: update\ndata: {\"ok\":true}\n\n", 1)), null, 1)
        val data = f.useCase.execute(f.request("one"), SSE_DATA)
        assertContains(data, "\"ok\": true"); assertFalse(data.contains("event: update"))
        assertContains(f.useCase.execute(f.request("one"), SSE_EVENTS), "event: update")
        f.rows[row.id] = row.copy(request = row.request.copy(head = row.request.head.copy(target = RequestTarget.Absolute(HttpScheme.fromToken("https"), Authority("grpc.test", 443), "/echo.Echo/Ping"),
            headers = listOf(HeaderField(HeaderName("Content-Type"), "application/grpc")))), response = row.response!!.copy(trailers = listOf(HeaderField(HeaderName("grpc-status"), "0"))))
        val method = f.useCase.execute(f.request("one"), GRPC_METHOD); assertContains(method, "echo.Echo"); assertContains(method, "Ping")
        assertContains(f.useCase.execute(f.request("one"), GRPC_METADATA), "grpc-status: 0")
    }
    @Test fun `structured JSON is parseable versioned ordered and keeps unavailable selected records`() = runTest {
        val f = TrafficCopyFixture(); f.row(responseBody = f.body("{\"ok\":true}".encodeToByteArray())); f.row("new", 3)
        val json = Json.parseToJsonElement(f.useCase.execute(f.request("one", "new", "removed"), STRUCTURED_JSON)).jsonObject
        assertEquals(1, json.getValue("version").jsonPrimitive.int)
        val items = json.getValue("items").jsonArray
        assertEquals(listOf("new", "one", "removed"), items.map { it.jsonObject.getValue("id").jsonPrimitive.content })
        assertEquals("unavailable", items.last().jsonObject.getValue("kind").jsonPrimitive.content)
        assertContains(items[1].toString(), "Response body")
    }
    @Test fun `all presets reject foreign source before payloads and fail atomically on oversized metadata`() = runTest {
        val f = TrafficCopyFixture(); val row = f.row(responseBody = f.body("private".encodeToByteArray()))
        for (preset in TrafficCopyPreset.entries) assertFailsWith<IllegalArgumentException>(preset.name) {
            f.useCase.execute(f.request("one").copy(source = TrafficSource.Device(RegisteredDeviceId("foreign"))), preset)
        }
        assertTrue(f.reads.isEmpty())
        f.rows[row.id] = row.copy(request = row.request.copy(head = row.request.head.copy(headers = listOf(HeaderField(HeaderName("Huge"), "x".repeat(4_194_304))))))
        for (preset in listOf(SUMMARY, STRUCTURED_JSON, CURL)) assertFailsWith<IllegalArgumentException> { f.useCase.execute(f.request("one"), preset) }
        assertEquals(f.leases, f.released)
    }
    @Test fun `curl shell quoting and binary stdin survive a real POSIX shell without executing network traffic`() = runTest {
        val f = TrafficCopyFixture(); val bytes = byteArrayOf(0, 10, 39, 36, -1, 127); val row = f.row(requestBody = f.body(bytes))
        f.rows[row.id] = row.copy(request = row.request.copy(head = row.request.head.copy(headers = listOf(HeaderField(HeaderName("X-Value"), "O'Reilly \$HOME `literal`"), HeaderField(HeaderName("Content-Length"), "6")))))
        val command = f.useCase.execute(f.request("one"), CURL)
        val root = Files.createTempDirectory("knet-copy-curl-")
        try {
            val args = root.resolve("args"); val input = root.resolve("stdin")
            val script = "curl() { printf '%s\\n' \"\$@\" > '" + args + "'; cat > '" + input + "'; }\n" + command
            val process = ProcessBuilder("/bin/sh", "-c", script).redirectErrorStream(true).start()
            val output = process.inputStream.readBytes().decodeToString(); assertEquals(0, process.waitFor(), output)
            assertContentEquals(bytes, Files.readAllBytes(input)); val copiedArgs = Files.readString(args)
            assertContains(copiedArgs, "O'Reilly \$HOME `literal`"); assertFalse(copiedArgs.contains("Content-Length"))
        } finally { root.toFile().deleteRecursively() }
        f.row(requestBody = f.body(ByteArray(100_000), BodyCaptureOutcome.Truncated(100_000), 200_000))
        assertContains(f.useCase.execute(f.request("one"), CURL), "complete request bytes are required")
    }

    @Test fun `GraphQL batch component copies retain operation boundaries without leaking unrequested fields`() = runTest {
        val f = TrafficCopyFixture(); f.row()
        val first = GraphQLHttpDocument(GraphQLHttpSource.JSON_BODY, queryText = "query SecretA { id }", variablesJson = "{\"id\":1}")
        val second = GraphQLHttpDocument(GraphQLHttpSource.JSON_BODY, queryText = "query SecretB { id }", variablesJson = "{\"id\":2}")
        f.graphQL = GraphQLHttpDocument(GraphQLHttpSource.JSON_BATCH, batchEntries = listOf(first, second))
        val variables = f.useCase.execute(f.request("one"), GRAPHQL_VARIABLES)
        assertContains(variables, "Operation 1: Variables"); assertContains(variables, "Operation 2: Variables")
        assertContains(variables, "id"); assertFalse(variables.contains("SecretA")); assertFalse(variables.contains("SecretB"))
        val queries = f.useCase.execute(f.request("one"), GRAPHQL_QUERY)
        assertContains(queries, "SecretA"); assertContains(queries, "SecretB"); assertFalse(queries.contains("Variables:"))
    }
    @Test fun `mixed HTTP opaque and missing records retain explicit availability and observed closure`() = runTest {
        val f = TrafficCopyFixture(); f.row(responseBody = f.body("response".encodeToByteArray())); val id = OpaqueFlowId("tls")
        f.flows[id] = OpaqueFlowSnapshot(id, ConnectionId("conn"), TrafficEndpoint("play.test", 443), "play.test", OpaqueTransportProtocol.TCP,
            OpaqueSecurityProtocol.TLS, null, null, startedAtEpochMillis = 2, completedAtEpochMillis = 3, uploadedBytes = 10, downloadedBytes = 20,
            state = OpaqueFlowState.COMPLETED, terminalOutcome = ExchangeTerminalOutcome.Completed,
            closure = OpaqueFlowClosure(OpaqueFlowCloseInitiator.SERVER, OpaqueFlowCloseReason.PEER_EOF, 3))
        val selected = CaptureExportSource.Selection(listOf(TrafficRecordId.Opaque(id), TrafficRecordId.Exchange(ExchangeId("one")), TrafficRecordId.Exchange(ExchangeId("missing"))), TrafficSource.Other)
        val response = f.useCase.execute(selected, RESPONSE_BODY)
        assertContains(response, "response"); assertContains(response, "opaque connection"); assertContains(response, "Cleared before copying")
        val closure = f.useCase.execute(selected, FAILURE_CLOSURE)
        assertContains(closure, "Closed by"); assertContains(closure, "Server"); assertContains(closure, "Peer eof")
        val json = Json.parseToJsonElement(f.useCase.execute(selected, STRUCTURED_JSON)).jsonObject.getValue("items").jsonArray
        assertEquals(listOf("opaque", "http", "unavailable"), json.map { it.jsonObject.getValue("kind").jsonPrimitive.content })
    }
    @Test fun `large valid selections fail the whole document budget without unbounded body reads`() = runTest {
        val f = TrafficCopyFixture(); val payload = f.body(ByteArray(65_536) { 120 })
        repeat(70) { f.row("item-$it", it.toLong(), responseBody = payload) }
        val source = f.request(*Array(70) { "item-$it" })
        for (preset in listOf(REQUEST_RESPONSE_BODIES, STRUCTURED_JSON)) assertFailsWith<IllegalArgumentException> { f.useCase.execute(source, preset) }
        assertEquals(65_536, f.maximumBody); assertEquals(f.leases, f.released)
    }

    @Test fun `single body copies preserve trailing spaces and CRLF exactly for direct paste`() = runTest {
        val f = TrafficCopyFixture(); val literal = "plain text  \r\nnext\t \r\n\r\n"
        f.row(requestBody = f.body(literal.encodeToByteArray()), responseBody = f.body(literal.encodeToByteArray()))
        assertEquals(literal, f.useCase.execute(f.request("one"), REQUEST_BODY))
        assertEquals(literal, f.useCase.execute(f.request("one"), RESPONSE_BODY))
    }
    @Test fun `curl refuses trailers incompatible protocols malformed headers and incomplete byte accounting`() = runTest {
        val f = TrafficCopyFixture(); val row = f.row()
        f.rows[row.id] = row.copy(request = row.request.copy(trailers = listOf(HeaderField(HeaderName("Checksum"), "value"))))
        assertContains(f.useCase.execute(f.request("one"), CURL), "trailers cannot be represented")
        f.rows[row.id] = row.copy(request = row.request.copy(head = row.request.head.copy(headers = listOf(HeaderField(HeaderName("X-Value"), "line\nInjected: x")))))
        assertContains(f.useCase.execute(f.request("one"), CURL), "control characters")
        f.rows[row.id] = row.copy(request = row.request.copy(head = row.request.head.copy(method = HttpMethod.CONNECT)))
        assertContains(f.useCase.execute(f.request("one"), CURL), "absolute HTTP or HTTPS")
        f.rows[row.id] = row.copy(request = row.request.copy(body = f.body("partial".encodeToByteArray(), observed = 100)))
        assertContains(f.useCase.execute(f.request("one"), CURL), "complete request bytes are required")
        f.rows[row.id] = row.copy(request = row.request.copy(head = row.request.head.copy(headers = listOf(HeaderField(HeaderName("X-Empty"), "")))))
        assertContains(f.useCase.execute(f.request("one"), CURL), "--header 'X-Empty;'")
    }

    private fun message(f: TrafficCopyFixture, row: HttpExchangeSnapshot, id: String, protocol: MessageProtocolId, direction: TrafficDirection, value: String, sequence: Long) =
        ProtocolMessageSnapshot(ProtocolMessageId(id), ConnectionId("connection"), row.id, null, protocol,
            if (protocol == MessageProtocolId.SSE) ProtocolMessageKind.RECORD else ProtocolMessageKind.TEXT, direction, sequence, 1, null, value.length.toLong(), false, null,
            f.body(value.encodeToByteArray()), ProtocolMessageState.COMPLETE)
}
