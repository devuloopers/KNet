package com.devuloopers.knet.data.desktop.export

import com.devuloopers.knet.application.contract.export.*
import com.devuloopers.knet.application.contract.traffic.*
import com.devuloopers.knet.application.contract.apistudio.*
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutionResponseHead
import com.devuloopers.knet.domain.clientNetwork.model.*
import com.devuloopers.knet.engine.session.FileBodyStore
import com.devuloopers.knet.traffic.id.*
import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.traffic.model.body.*
import com.devuloopers.knet.traffic.model.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.test.*

/** Real file/archive tests; no desktop, pairing, CA or user's persistent capture is touched. */
class CaptureExportTest {
    @Test fun `selected export keeps exact order reports removed items and never expands to the query`() = runBlocking { fixture {
        repeat(5) { rows += row(index = it) }
        val source = CaptureExportSource.Selection(listOf(TrafficRecordId.Exchange(rows[3].id), TrafficRecordId.Exchange(ExchangeId("removed")), TrafficRecordId.Exchange(rows[1].id)), TrafficSource.Other)
        val prepared = repository.prepare(source)
        assertEquals(2L, prepared.records); assertEquals(1L, prepared.unavailableRecords)
        val target = File(root, "selection.knetcapture")
        repository.write(prepared, CaptureExportOptions(), target.path) {}
        assertEquals(listOf(rows[3].id.value, rows[1].id.value), records(target).map { it["id"]!!.jsonPrimitive.content })
        assertTrue(queries.isEmpty())
        repository.release(prepared)
    } }

    @Test fun `selected export cannot retarget another authenticated source`() = runBlocking { fixture {
        rows += row()
        assertFailsWith<IllegalArgumentException> { repository.prepare(CaptureExportSource.Selection(listOf(TrafficRecordId.Exchange(rows.single().id)), TrafficSource.Device(com.devuloopers.knet.identity.RegisteredDeviceId("different")))) }
        assertFailsWith<IllegalArgumentException> { CaptureExportSource.Selection(emptyList(), TrafficSource.Other) }
        assertFailsWith<IllegalArgumentException> { CaptureExportSource.Selection(listOf(TrafficRecordId.Exchange(rows.single().id), TrafficRecordId.Exchange(rows.single().id)), TrafficSource.Other) }
    } }

    @Test fun `actual native and HAR exports roundtrip with independent imported bytes and explicit omission`() = runBlocking { fixture {
        val bytes = ByteArray(131_073) { (it % 251).toByte() }
        rows += row(body(bytes))
        val prepared = repository.prepare(CaptureExportSource.Item(TrafficRecordId.Exchange(rows.single().id)))
        val importer = com.devuloopers.knet.data.desktop.importcapture.DesktopCaptureImportRepository(File(root, "imports"))
        for (format in CaptureExportFormat.entries) for (payloads in listOf(true, false)) {
            val target = File(root, "roundtrip-$payloads.${format.extension}")
            repository.write(prepared, CaptureExportOptions(format = format, includePayloads = payloads), target.path) {}
            val imported = importer.open(target.path) {}
            val record = importer.record(imported.id, 0)
            assertEquals("https://example.test/a?q=%E2%9C%93", record.destination)
            assertEquals(listOf("one", "two"), record.request!!.headers.filter { it.first == "X-Duplicate" }.map { it.second })
            if (payloads) assertContentEquals(bytes, importer.body(imported.id, record.response!!.body).bytes)
            else {
                assertTrue(imported.unavailableBodies > 0)
                assertTrue(record.response!!.body.state in setOf(com.devuloopers.knet.application.contract.importcapture.ImportedBodyState.OMITTED,
                    com.devuloopers.knet.application.contract.importcapture.ImportedBodyState.UNAVAILABLE))
            }
            importer.close(imported.id)
        }
        repository.release(prepared)
    } }

    private class Fixture {
        val root = Files.createTempDirectory("knet-export-test").toFile()
        val store = FileBodyStore(File(root, "bodies"))
        val studio = DesktopStudioExportRecorder(File(root, "studio"))
        val rows = mutableListOf<HttpExchangeSnapshot>()
        val queries = mutableListOf<TrafficPageQuery>()
        val framed = mutableListOf<com.devuloopers.knet.traffic.model.message.ProtocolMessageSnapshot>()
        val opaque = mutableListOf<OpaqueFlowSnapshot>()
        var bodySequence = 0L
        var reads = 0
        private val query = object : TrafficQuery, ProtocolMessageQuery {
            override val generations: Flow<TrafficGeneration> = emptyFlow()
            override suspend fun query(query: TrafficPageQuery): TrafficPage {
                queries += query
                val start = query.cursor?.value?.toInt() ?: 0
                val values = rows.drop(start).take(query.limit)
                return TrafficPage(values.mapIndexed { index, exchange -> TrafficPageItem(TrafficCaptureSequence((start + index + 1).toLong()), TrafficHistorySequence((start + index + 1).toLong()), exchange) },
                    nextCursor = (start + values.size).takeIf { it < rows.size }?.let { TrafficPageCursor(it.toString()) }, totalCount = rows.size.toLong(), generation = 1)
            }
            override suspend fun getExchange(exchangeId: ExchangeId) = rows.firstOrNull { it.id == exchangeId }
            override suspend fun getOpaqueFlow(flowId: OpaqueFlowId) = opaque.firstOrNull { it.id == flowId }
            override suspend fun readBody(bodyId: BodyId, range: BodyRange): BodyChunk { reads++; return store.readBody(bodyId, range) }
            override fun observeChanges(exchangeId: ExchangeId): Flow<Long> = emptyFlow()
            override suspend fun queryMessages(query: ProtocolMessagePageQuery): ProtocolMessagePage {
                val values = framed.filter { it.exchangeId == query.exchangeId }
                val start = query.cursor?.value?.toInt() ?: 0
                val page = values.drop(start).take(query.limit)
                return ProtocolMessagePage(page, (start + page.size).takeIf { it < values.size }?.let { ProtocolMessagePageCursor(it.toString()) }, values.size.toLong())
            }
        }
        val repository = DesktopCaptureExportRepository(null, query, query, store, studio, File(root, "exports"))
        suspend fun body(bytes: ByteArray): MessageBodyRef {
            val writer = store.openWrite(BodyId("body-${++bodySequence}-${bytes.size}"), BodyWritePolicy(bytes.size.toLong()))
            if (bytes.isNotEmpty()) writer.append(bytes)
            return MessageBodyRef.Available(writer.complete().body)
        }
        fun row(body: MessageBodyRef = MessageBodyRef.Empty, status: Int = 200, index: Int = rows.size): HttpExchangeSnapshot = HttpExchangeSnapshot(
            ExchangeId("exchange-$index"), request = HttpRequestSnapshot(RequestHead(HttpMethod.GET, RequestTarget.Custom("https://example.test/a?q=%E2%9C%93"), ApplicationProtocol.fromToken("HTTP/1.1"),
                listOf(HeaderField(HeaderName("Authorization"), "Bearer private"), HeaderField(HeaderName("X-Duplicate"), "one"), HeaderField(HeaderName("X-Duplicate"), "two")))),
            response = HttpResponseSnapshot(ResponseHead(ApplicationProtocol.fromToken("HTTP/1.1"), HttpStatus(status), "OK", listOf(HeaderField(HeaderName("Set-Cookie"), "secret=x"))), body),
            state = ExchangeState.COMPLETED, startedAtEpochMillis = 1_700_000_000_000 + index.toLong(), timings = ExchangeTimings(totalMillis = 12),
        )
        suspend fun close() { studio.close(); studio.awaitCleanup(); root.deleteRecursively() }
    }
    private suspend fun fixture(block: suspend Fixture.() -> Unit) { val fixture = Fixture(); try { fixture.block() } finally { fixture.close() } }
    private fun records(file: File): List<JsonObject> = ZipFile(file).use { zip -> zip.getInputStream(zip.getEntry("records.jsonl")).bufferedReader().readLines().map { Json.parseToJsonElement(it).jsonObject } }

    @Test fun `native archive retains binary bytes integrity ordered headers and redacts credentials without editing capture`() = runBlocking { fixture {
        val bytes = ByteArray(200_003) { (it % 251).toByte() }
        rows += row(body(bytes))
        val prepared = repository.prepare(CaptureExportSource.Item(TrafficRecordId.Exchange(rows.single().id)))
        val target = File(root, "capture.knetcapture")
        repository.write(prepared, CaptureExportOptions(), target.path) {}
        val record = records(target).single()
        assertEquals("Bearer private", rows.single().request.head.headers.first().value)
        assertEquals("[REDACTED]", record["request"]!!.jsonObject["headers"]!!.jsonArray.first().jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals(3, record["request"]!!.jsonObject["headers"]!!.jsonArray.size)
        val body = record["response"]!!.jsonObject["body"]!!.jsonObject
        ZipFile(target).use { zip -> assertContentEquals(bytes, zip.getInputStream(zip.getEntry(body["entry"]!!.jsonPrimitive.content)).readBytes()) }
        assertEquals(64, body["sha256"]!!.jsonPrimitive.content.length)
        repository.release(prepared)
    } }

    @Test fun `bulk export traverses beyond UI window and holds original filter scope`() = runBlocking { fixture {
        repeat(1107) { rows += row(index = it) }
        val source = CaptureExportSource.Traffic(TrafficPageQuery(limit = 1000, searchContains = "fixed", recordFilter = TrafficRecordFilter.FAILED))
        val prepared = repository.prepare(source)
        assertEquals(1107L, prepared.records); assertTrue(queries.size > 1)
        assertTrue(queries.all { it.searchContains == "fixed" && it.recordFilter == TrafficRecordFilter.FAILED && it.limit == 128 })
        val target = File(root, "all.knetcapture"); repository.write(prepared, CaptureExportOptions(), target.path) {}
        assertEquals(1107, records(target).size)
        repository.release(prepared)
    } }

    @Test fun `prepared snapshot survives Clear and never reads mutable payload storage during save`() = runBlocking { fixture {
        val ref = body("original".encodeToByteArray()); rows += row(ref)
        val prepared = repository.prepare(CaptureExportSource.Item(TrafficRecordId.Exchange(rows.single().id)))
        rows.clear(); store.delete((ref as MessageBodyRef.Available).body.id)
        val target = File(root, "frozen.knetcapture"); repository.write(prepared, CaptureExportOptions(), target.path) {}
        ZipFile(target).use { zip -> assertEquals("original", zip.getInputStream(zip.getEntry("payloads/1.bin")).bufferedReader().readText()) }
        repository.release(prepared)
    } }

    @Test fun `metadata only omits payload entries and digests explicitly`() = runBlocking { fixture {
        rows += row(body("secret".encodeToByteArray()))
        val prepared = repository.prepare(CaptureExportSource.Item(TrafficRecordId.Exchange(rows.single().id)))
        val target = File(root, "meta.knetcapture"); repository.write(prepared, CaptureExportOptions(includePayloads = false), target.path) {}
        val value = records(target).single()["response"]!!.jsonObject["body"]!!.jsonObject
        assertEquals("OMITTED_BY_USER", value["state"]!!.jsonPrimitive.content); assertNull(value["entry"]); assertNull(value["sha256"])
        ZipFile(target).use { zip -> assertEquals(2, zip.size()) }
        repository.release(prepared)
    } }

    @Test fun `missing body is honest evidence and never becomes an empty successful response`() = runBlocking { fixture {
        val ref = body("missing".encodeToByteArray()) as MessageBodyRef.Available
        rows += row(ref); store.delete(ref.body.id)
        val prepared = repository.prepare(CaptureExportSource.Item(TrafficRecordId.Exchange(rows.single().id)))
        assertEquals(1L, prepared.missingPayloads)
        val target = File(root, "missing.knetcapture"); repository.write(prepared, CaptureExportOptions(), target.path) {}
        assertEquals("MISSING", records(target).single()["response"]!!.jsonObject["body"]!!.jsonObject["state"]!!.jsonPrimitive.content)
        repository.release(prepared)
    } }

    @Test fun `explicit full credentials choice retains original header values`() = runBlocking { fixture {
        rows += row(); val prepared = repository.prepare(CaptureExportSource.Item(TrafficRecordId.Exchange(rows.single().id)))
        val target = File(root, "full.knetcapture"); repository.write(prepared, CaptureExportOptions(includeCredentials = true), target.path) {}
        assertTrue(records(target).single().toString().contains("Bearer private")); repository.release(prepared)
    } }

    @Test fun `HAR is parseable and preserves original binary response as standard base64`() = runBlocking { fixture {
        val bytes = ByteArray(133_333) { it.toByte() }; rows += row(body(bytes), 503)
        val prepared = repository.prepare(CaptureExportSource.Item(TrafficRecordId.Exchange(rows.single().id)))
        val target = File(root, "capture.har"); repository.write(prepared, CaptureExportOptions(format = CaptureExportFormat.HAR), target.path) {}
        val entry = Json.parseToJsonElement(target.readText()).jsonObject["log"]!!.jsonObject["entries"]!!.jsonArray.single().jsonObject
        assertEquals(503, entry["response"]!!.jsonObject["status"]!!.jsonPrimitive.int)
        val content = entry["response"]!!.jsonObject["content"]!!.jsonObject
        assertNull(content["_capture"]!!.jsonObject["entry"])
        assertContentEquals(bytes, java.util.Base64.getDecoder().decode(content["text"]!!.jsonPrimitive.content))
        assertEquals("✓", entry["request"]!!.jsonObject["queryString"]!!.jsonArray.single().jsonObject["value"]!!.jsonPrimitive.content)
        repository.release(prepared)
    } }

    @Test fun `failed destination leaves old file intact and cancellation removes only task temporary files`() = runBlocking { fixture {
        rows += row(body(ByteArray(140_000) { 7 })); val prepared = repository.prepare(CaptureExportSource.Item(TrafficRecordId.Exchange(rows.single().id)))
        val target = File(root, "keep.knetcapture").apply { writeText("original") }
        assertFailsWith<CancellationException> { repository.write(prepared, CaptureExportOptions(), target.path) { throw CancellationException("test") } }
        assertEquals("original", target.readText()); assertTrue(root.listFiles()!!.none { it.name.startsWith(".knet-export-") })
        repository.release(prepared); repository.release(prepared)
        assertFailsWith<IllegalArgumentException> { repository.write(prepared, CaptureExportOptions(), target.path) {} }
    } }

    @Test fun `HAR cookie projections obey privacy and metadata only requests retain omission evidence`() = runBlocking { fixture {
        val original = row(body("request secret".encodeToByteArray()))
        rows += original.copy(request = original.request.copy(body = original.response!!.body,
            head = original.request.head.copy(headers = listOf(HeaderField(HeaderName("Cookie"), "session=abc==; mode=qa")))),
            response = original.response!!.copy(head = original.response!!.head.copy(headers = listOf(
                HeaderField(HeaderName("Set-Cookie"), "first=one; Path=/; HttpOnly; Secure"),
                HeaderField(HeaderName("Set-Cookie"), "second=two; Path=/api; SameSite=Lax"),
            ))))
        val prepared = repository.prepare(CaptureExportSource.Item(TrafficRecordId.Exchange(rows.single().id)))
        suspend fun export(credentials: Boolean): JsonObject {
            val target = File(root, "cookies-$credentials.har")
            repository.write(prepared, CaptureExportOptions(CaptureExportFormat.HAR, includePayloads = false, includeCredentials = credentials), target.path) {}
            return Json.parseToJsonElement(target.readText()).jsonObject["log"]!!.jsonObject["entries"]!!.jsonArray.single().jsonObject
        }
        val redacted = export(false)
        assertTrue(redacted["request"]!!.jsonObject["cookies"]!!.jsonArray.isEmpty())
        assertTrue(redacted["response"]!!.jsonObject["cookies"]!!.jsonArray.isEmpty())
        assertEquals("OMITTED_BY_USER", redacted["request"]!!.jsonObject["_capture"]!!.jsonObject["state"]!!.jsonPrimitive.content)
        assertFalse(redacted.toString().contains("session=abc"))
        val retained = export(true)
        assertEquals(listOf("abc==", "qa"), retained["request"]!!.jsonObject["cookies"]!!.jsonArray.map { it.jsonObject["value"]!!.jsonPrimitive.content })
        val received = retained["response"]!!.jsonObject["cookies"]!!.jsonArray
        assertEquals(listOf("first", "second"), received.map { it.jsonObject["name"]!!.jsonPrimitive.content })
        assertTrue(received.first().jsonObject["httpOnly"]!!.jsonPrimitive.boolean)
        assertTrue(received.first().jsonObject["secure"]!!.jsonPrimitive.boolean)
        repository.release(prepared)
    } }

    @Test fun `body read lease defers both cleanup paths and closes idempotently`() = runBlocking { fixture {
        val ref = body("pinned".encodeToByteArray()) as MessageBodyRef.Available
        val lease = store.retainExportReads()
        assertFailsWith<IllegalStateException> { store.delete(ref.body.id) }
        assertFailsWith<IllegalStateException> { store.deleteByStorageKey(store.storageKey(ref.body.id)) }
        lease.close(); lease.close()
        assertEquals(BodyDeleteResult.DELETED, store.delete(ref.body.id))
    } }

    @Test fun `Studio accepts every actual HTTP status including empty gateway responses and exports original unformatted bytes`() = runBlocking { fixture {
        for (status in listOf(200, 204, 304, 400, 500, 502, 503, 504)) {
            val session = studio.http(StudioExportHttpRequest("https://example.test", HttpMethod.GET, emptyList(), OutboundRequestBody.None))
            assertFalse(session.eligible.value)
            session.response(HttpExecutionResponseHead(status, "", emptyMap(), emptyMap(), ApplicationProtocol.fromToken("HTTP/1.1")))
            val bytes = "{\"compact\":true}".encodeToByteArray()
            if (status != 204 && status != 304) session.append(bytes)
            session.complete(ExecutionResult(status, "", responseBody = "pretty UI text", responseSizeBytes = if (status in listOf(204,304)) 0 else bytes.size.toLong()))
            assertTrue(session.eligible.value)
            val prepared = repository.prepare(CaptureExportSource.Studio(session.id))
            val target = File(root, "studio-$status.knetcapture"); repository.write(prepared, CaptureExportOptions(), target.path) {}
            assertFalse(records(target).single().toString().contains("pretty UI text"))
            repository.release(prepared); session.release()
        }
    } }

    @Test fun `Studio refuses draft or local failure without server evidence`() = runBlocking { fixture {
        val session = studio.http(StudioExportHttpRequest("https://example.test", HttpMethod.GET, emptyList(), OutboundRequestBody.None))
        session.complete(ExecutionResult(0, "DNS error", failureReason = NetworkFailureReason.HostNotFound("example.test", "test")))
        assertFalse(session.eligible.value)
        assertFailsWith<IllegalStateException> { repository.prepare(CaptureExportSource.Studio(session.id)) }
        session.release()
    } }

    @Test fun `Studio partial snapshot retains bytes across document release and cannot read another session`() = runBlocking { fixture {
        val session = studio.http(StudioExportHttpRequest("https://example.test/original", HttpMethod.POST, emptyList(), OutboundRequestBody.Json("original request")))
        session.response(HttpExecutionResponseHead(200, "", mapOf("Content-Type" to "text/event-stream"), emptyMap(), ApplicationProtocol.fromToken("HTTP/1.1")))
        session.append("data: hello\n\n".encodeToByteArray()); session.interrupted()
        val prepared = repository.prepare(CaptureExportSource.Studio(session.id)); session.release()
        val target = File(root, "partial.knetcapture"); repository.write(prepared, CaptureExportOptions(), target.path) {}
        assertEquals("PARTIAL", records(target).single()["state"]!!.jsonPrimitive.content)
        assertTrue(records(target).single().toString().contains("/original"))
        repository.release(prepared)
    } }
    @Test fun `real Direct HTTP execution exports encoded multipart binary response and duplicate headers`() = runBlocking { fixture {
        val responseBytes = ByteArray(70_001) { (it % 255).toByte() }
        var received = byteArrayOf()
        var requestType = ""
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/upload") { exchange ->
            received = exchange.requestBody.readBytes()
            requestType = exchange.requestHeaders.getFirst("Content-Type").orEmpty()
            exchange.responseHeaders.add("X-Multiple", "first"); exchange.responseHeaders.add("X-Multiple", "second")
            exchange.responseHeaders.add("Content-Type", "application/octet-stream")
            exchange.sendResponseHeaders(503, responseBytes.size.toLong())
            exchange.responseBody.use { it.write(responseBytes) }
        }
        server.start()
        val client = com.devuloopers.knet.core.http.client.KNetApiClient(configuration = com.devuloopers.knet.core.http.config.HttpClientConfiguration(retryCount = 0))
        try {
            val useCase = com.devuloopers.knet.application.usecase.apistudio.ExecuteApiStudioRequestUseCase(
                com.devuloopers.knet.domain.clientNetwork.usecase.ExecuteClientApiRequestUseCase(client),
                com.devuloopers.knet.domain.clientNetwork.usecase.FormatResponseBodyUseCase(),
                com.devuloopers.knet.application.contract.script.UnavailableScriptExecutor, Dispatchers.IO, studio)
            val request = com.devuloopers.knet.domain.collection.model.SavedApiRequest("request", "upload", method = HttpMethod.POST,
                url = "http://127.0.0.1:${server.address.port}/upload", httpVersionPreference = HttpVersionPreference.HTTP_1_1,
                body = com.devuloopers.knet.domain.collection.model.ApiRequestBody(type = RequestBodyType.MULTIPART,
                    formDataFields = listOf(com.devuloopers.knet.domain.collection.model.ApiRequestBodyField("field", "name", "✓ field"))))
            val events = useCase.executeStreaming(request, null).toList()
            val completed = events.filterIsInstance<com.devuloopers.knet.application.usecase.apistudio.ApiStudioHttpExecutionEvent.Completed>().single()
            val archive = assertNotNull(completed.value.exportSession); assertTrue(archive.eligible.value)
            val prepared = repository.prepare(CaptureExportSource.Studio(archive.id))
            val target = File(root, "real.knetcapture"); repository.write(prepared, CaptureExportOptions(), target.path) {}
            val record = records(target).single()
            val sent = record["request"]!!.jsonObject
            assertEquals("TRANSPORT_REQUEST", sent["representation"]!!.jsonPrimitive.content)
            assertTrue(requestType.startsWith("multipart/form-data; boundary="))
            ZipFile(target).use { zip ->
                assertContentEquals(received, zip.getInputStream(zip.getEntry(sent["body"]!!.jsonObject["entry"]!!.jsonPrimitive.content)).readBytes())
                assertContentEquals(responseBytes, zip.getInputStream(zip.getEntry(record["response"]!!.jsonObject["body"]!!.jsonObject["entry"]!!.jsonPrimitive.content)).readBytes())
            }
            val duplicates = record["response"]!!.jsonObject["headers"]!!.jsonArray.filter { it.jsonObject["name"]!!.jsonPrimitive.content.equals("x-multiple", true) }
            assertEquals(2, duplicates.size)
            repository.release(prepared); archive.release()
        } finally { client.close(); server.stop(0) }
    } }

    @Test fun `native session needs server proof and retains inbound binary messages independently of visible window`() = runBlocking { fixture {
        val kind = com.devuloopers.knet.domain.request.descriptor.RequestKindId.WEBSOCKET
        val command = ApiStudioProtocolExecutionCommand(ApiStudioProtocolDocument("doc", "Socket", kind, 1, byteArrayOf()))
        val draft = ApiStudioProtocolDraft("doc", "Socket", "example.test", 443, true, "socket", 1000,
            listOf(ApiStudioProtocolMetadataEntry("Authorization", "secret")), emptyList(), null, "wss://example.test")
        val session = studio.protocol(command, draft)
        session.event(ApiStudioProtocolExecutionEvent.Started("local start"))
        session.event(ApiStudioProtocolExecutionEvent.Message(ApiStudioProtocolMessage(1, ApiStudioProtocolMessageDirection.OUTBOUND, "text/plain", "send", "send".encodeToByteArray())))
        assertFalse(session.eligible.value)
        val original = byteArrayOf(0, -1, 2, 3)
        session.event(ApiStudioProtocolExecutionEvent.Message(ApiStudioProtocolMessage(2, ApiStudioProtocolMessageDirection.INBOUND, "application/octet-stream", "formatted preview", original)))
        assertTrue(session.eligible.value)
        val prepared = repository.prepare(CaptureExportSource.Studio(session.id))
        assertEquals(0L, prepared.httpExchanges); assertEquals(2L, prepared.messages)
        val target = File(root, "socket.knetcapture"); repository.write(prepared, CaptureExportOptions(), target.path) {}
        val inbound = records(target).single { it["direction"]?.jsonPrimitive?.content == "INBOUND" }
        ZipFile(target).use { zip -> assertContentEquals(original, zip.getInputStream(zip.getEntry(inbound["body"]!!.jsonObject["entry"]!!.jsonPrimitive.content)).readBytes()) }
        assertFalse(records(target).any { it.toString().contains("formatted preview") })
        assertFailsWith<IllegalArgumentException> { repository.write(prepared, CaptureExportOptions(CaptureExportFormat.HAR), File(root, "no.har").path) {} }
        repository.release(prepared); session.release()
    } }

    @Test fun `trailers only server rejection qualifies but local native failure does not`() = runBlocking { fixture {
        val kind = com.devuloopers.knet.domain.request.descriptor.RequestKindId.GRPC
        val command = ApiStudioProtocolExecutionCommand(ApiStudioProtocolDocument("doc", "RPC", kind, 1, byteArrayOf()))
        val draft = ApiStudioProtocolDraft("doc", "RPC", "example.test", 443, true, "service/Call", 1000, emptyList(), emptyList(), null)
        val local = studio.protocol(command, draft)
        local.event(ApiStudioProtocolExecutionEvent.Failed("UNAVAILABLE", "connection refused", false))
        assertFalse(local.eligible.value); local.release()
        val server = studio.protocol(command, draft)
        server.event(ApiStudioProtocolExecutionEvent.Failed("PERMISSION_DENIED", "server rejected", false, serverResponseReceived = true,
            trailers = listOf("grpc-status" to "7")))
        assertTrue(server.eligible.value)
        val prepared = repository.prepare(CaptureExportSource.Studio(server.id))
        val target = File(root, "rpc.knetcapture"); repository.write(prepared, CaptureExportOptions(), target.path) {}
        assertTrue(records(target).any { it["trailers"]?.toString()?.contains("grpc-status") == true })
        repository.release(prepared); server.release()
    } }

    @Test fun `untrusted metadata equal to former HAR sentinel stays literal JSON`() = runBlocking { fixture {
        val original = row(body("payload".encodeToByteArray()))
        rows += original.copy(request = original.request.copy(head = original.request.head.copy(headers = listOf(HeaderField(HeaderName("X-Text"), "__KNET_PAYLOAD__")))))
        val prepared = repository.prepare(CaptureExportSource.Item(TrafficRecordId.Exchange(rows.single().id)))
        val target = File(root, "literal.har"); repository.write(prepared, CaptureExportOptions(CaptureExportFormat.HAR), target.path) {}
        assertTrue(Json.parseToJsonElement(target.readText()).toString().contains("__KNET_PAYLOAD__"))
        repository.release(prepared)
    } }

    @Test fun `Traffic exports framed WebSocket gRPC and SSE bytes with sequence direction and pagination`() = runBlocking { fixture {
        rows += row()
        val body = body(byteArrayOf(0, -2, 3))
        val protocols = listOf(com.devuloopers.knet.traffic.model.message.MessageProtocolId.GRPC,
            com.devuloopers.knet.traffic.model.message.MessageProtocolId.WEBSOCKET,
            com.devuloopers.knet.traffic.model.message.MessageProtocolId.SSE)
        repeat(140) { index -> framed += com.devuloopers.knet.traffic.model.message.ProtocolMessageSnapshot(
            ProtocolMessageId("message-$index"), ConnectionId("connection"), rows.single().id, null, protocols[index % 3],
            com.devuloopers.knet.traffic.model.message.ProtocolMessageKind.DATA,
            if (index % 2 == 0) TrafficDirection.CLIENT_TO_SERVER else TrafficDirection.SERVER_TO_CLIENT,
            index.toLong(), 1700000000000 + index.toLong(), 3, 3, false, null, body,
            com.devuloopers.knet.traffic.model.message.ProtocolMessageState.COMPLETE) }
        val prepared = repository.prepare(CaptureExportSource.Item(TrafficRecordId.Exchange(rows.single().id)))
        assertEquals(140L, prepared.messages)
        val target = File(root, "framed.knetcapture"); repository.write(prepared, CaptureExportOptions(), target.path) {}
        val values = records(target).filter { it["kind"]!!.jsonPrimitive.content == "message" }
        assertEquals((0L..139L).toList(), values.map { it["sequence"]!!.jsonPrimitive.long })
        assertEquals(setOf("grpc", "websocket", "sse"), values.map { it["protocol"]!!.jsonPrimitive.content }.toSet())
        ZipFile(target).use { zip -> values.forEach { message -> assertContentEquals(byteArrayOf(0, -2, 3), zip.getInputStream(zip.getEntry(message["body"]!!.jsonObject["entry"]!!.jsonPrimitive.content)).readBytes()) } }
        repository.release(prepared)
    } }

    @Test fun `TLS rejection and UDP QUIC export metadata without invented payload and block incompatible HAR`() = runBlocking { fixture {
        for ((index, security) in listOf(OpaqueSecurityProtocol.TLS, OpaqueSecurityProtocol.QUIC).withIndex()) {
            val value = OpaqueFlowSnapshot(OpaqueFlowId("flow-$index"), ConnectionId("connection-$index"), TrafficEndpoint("example.test", 443), "example.test",
                if (security == OpaqueSecurityProtocol.TLS) OpaqueTransportProtocol.TCP else OpaqueTransportProtocol.UDP,
                security, null, null, policyAction = OpaqueFlowPolicyAction.TUNNEL,
                startedAtEpochMillis = 1700000000000, completedAtEpochMillis = null, uploadedBytes = 100, downloadedBytes = 200,
                state = OpaqueFlowState.ACTIVE, terminalOutcome = null)
            opaque += value
            val prepared = repository.prepare(CaptureExportSource.Item(TrafficRecordId.Opaque(value.id)))
            val target = File(root, "opaque-$index.knetcapture"); repository.write(prepared, CaptureExportOptions(), target.path) {}
            val record = records(target).single(); assertEquals(security.name, record["security"]!!.jsonPrimitive.content)
            assertEquals("ENCRYPTED_OR_NOT_CAPTURED", record["payloadAvailability"]!!.jsonPrimitive.content)
            assertNull(record["response"])
            assertFailsWith<IllegalArgumentException> { repository.write(prepared, CaptureExportOptions(CaptureExportFormat.HAR), File(root, "bad.har").path) {} }
            repository.release(prepared)
        }
    } }

    @Test fun `HAR textual request keeps Unicode quotes slashes controls and raw JSON instead of base64`() = runBlocking { fixture {
        val text = "{\"field\":\"✓ \\\"quoted\\\" \\\\ path\"}\n\t"
        val requestBody = body(text.encodeToByteArray())
        val original = row()
        rows += original.copy(request = original.request.copy(body = requestBody))
        val prepared = repository.prepare(CaptureExportSource.Item(TrafficRecordId.Exchange(rows.single().id)))
        val target = File(root, "text.har"); repository.write(prepared, CaptureExportOptions(CaptureExportFormat.HAR), target.path) {}
        val post = Json.parseToJsonElement(target.readText()).jsonObject["log"]!!.jsonObject["entries"]!!.jsonArray.single().jsonObject["request"]!!.jsonObject["postData"]!!.jsonObject
        assertEquals(text, post["text"]!!.jsonPrimitive.content); assertNull(post["_encoding"])
        repository.release(prepared)
    } }

    @Test fun `SSE live response can snapshot before completion without waiting for new events`() = runBlocking { fixture {
        val session = studio.http(StudioExportHttpRequest("https://example.test/events", HttpMethod.GET, emptyList(), OutboundRequestBody.None))
        session.response(HttpExecutionResponseHead(200, "OK", mapOf("Content-Type" to "text/event-stream"), emptyMap(), ApplicationProtocol.fromToken("HTTP/1.1")))
        assertTrue(session.eligible.value)
        session.append("data: first\n\n".encodeToByteArray())
        val prepared = repository.prepare(CaptureExportSource.Studio(session.id))
        session.append("data: later\n\n".encodeToByteArray())
        val target = File(root, "live.knetcapture"); repository.write(prepared, CaptureExportOptions(), target.path) {}
        assertEquals("ACTIVE_AT_SNAPSHOT", records(target).single()["state"]!!.jsonPrimitive.content)
        ZipFile(target).use { zip -> assertEquals("data: first\n\n", zip.getInputStream(zip.getEntry("payloads/response.bin")).bufferedReader().readText()) }
        repository.release(prepared); session.release()
    } }

    @Test fun `32 MiB binary archive uses bounded retention and bulk output writes`() = runBlocking { fixture {
        withTimeout(30_000) {
            val block = kotlin.random.Random(41).nextBytes(1_048_576)
            val size = 32L * block.size
            val writer = store.openWrite(BodyId("large"), BodyWritePolicy(size))
            repeat(32) { writer.append(block) }
            rows += row(MessageBodyRef.Available(writer.complete().body))
            fun usedHeap(): Long { val runtime = Runtime.getRuntime(); return runtime.totalMemory() - runtime.freeMemory() }
            System.gc(); Thread.sleep(30)
            val before = usedHeap(); val started = System.nanoTime()
            val prepared = repository.prepare(CaptureExportSource.Item(TrafficRecordId.Exchange(rows.single().id)))
            val target = File(root, "large.knetcapture")
            repository.write(prepared, CaptureExportOptions(), target.path) {}
            var verified = 0L
            ZipFile(target).use { zip -> zip.getInputStream(zip.getEntry("payloads/1.bin")).use { input ->
                val buffer = ByteArray(65_536)
                while (true) {
                    val count = input.read(buffer); if (count < 0) break
                    for (index in 0 until count) assertEquals(block[((verified + index) % block.size).toInt()], buffer[index])
                    verified += count
                }
            } }
            assertEquals(size, verified)
            repository.release(prepared)
            System.gc(); Thread.sleep(30)
            val delta = (usedHeap() - before).coerceAtLeast(0)
            assertTrue(delta < 24L * 1024 * 1024, "Export must not retain whole binary bodies: $delta bytes")
            println("EXPORT_RESOURCE bytes=$verified retainedHeapDelta=$delta elapsedMillis=${(System.nanoTime() - started) / 1_000_000}")
        }
    } }

}
