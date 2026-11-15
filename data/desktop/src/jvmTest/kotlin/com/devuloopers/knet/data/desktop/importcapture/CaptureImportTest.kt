package com.devuloopers.knet.data.desktop.importcapture

import com.devuloopers.knet.application.contract.importcapture.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.*
import kotlin.test.*

/** Real files exercise streaming, private indexing, format fidelity and hostile-input rollback. */
class CaptureImportTest {
    @Test fun `HAR from tool exports preserves duplicate headers URL query binary and arbitrary key order`() = runBlocking {
        fixture { root, repository ->
            val bytes = byteArrayOf(0, 1, 127, -1)
            val input = root.resolve("misleading.chls").apply { writeText(har(listOf(entry(responseText = java.util.Base64.getEncoder().encodeToString(bytes), encoding = "base64")))) }
            val capture = repository.open(input.path) {}
            assertEquals(CaptureImportFormat.HAR, capture.format); assertEquals(1, capture.records.toInt())
            val row = repository.page(capture.id, 0).single()
            assertEquals("https://same.test/graphql?operationName=Q&x=1&x=2", row.destination)
            assertNull(row.request, "Page summaries cannot retain full headers/payload metadata")
            assertTrue(row.metadataJson.isEmpty())
            val full = repository.record(capture.id, 0)
            assertEquals(listOf("a", "b"), full.request!!.headers.map { it.second })
            assertEquals(200, full.status); assertEquals(125.5, full.durationMillis)
            assertContentEquals(bytes, repository.body(capture.id, full.response!!.body).bytes)
            assertTrue(full.response!!.body.decoded)
            assertTrue(input.isFile)
            repository.close(capture.id)
            assertFailsWith<IllegalArgumentException> { repository.page(capture.id, 0) }
        }
    }
    @Test fun `missing body differs from captured zero bytes and truncated originals stay partial`() = runBlocking {
        fixture { root, repository ->
            val input = root.resolve("missing.har").apply { writeText(har(listOf(
                entry(responseText = null), entry(responseText = ""), entry(responseText = "prefix", state = "TRUNCATED")))) }
            val capture = repository.open(input.path) {}
            assertEquals(2L, capture.unavailableBodies)
            val records = (0L..2).map { repository.record(capture.id, it) }
            assertEquals(ImportedBodyState.UNAVAILABLE, records[0].response!!.body.state)
            assertEquals(ImportedBodyState.COMPLETE, records[1].response!!.body.state)
            assertContentEquals(byteArrayOf(), repository.body(capture.id, records[1].response!!.body).bytes)
            assertEquals(ImportedBodyState.TRUNCATED, records[2].response!!.body.state)
        }
    }
    @Test fun `pages remain indexed and selected previews disclose display truncation`() = runBlocking {
        fixture { root, repository ->
            val input = root.resolve("large.har").apply { writeText(har(List(257) { entry(responseText = "x".repeat(if (it == 0) 1_100_000 else 1)) })) }
            val capture = repository.open(input.path) {}
            assertEquals((128L..255).toList(), repository.page(capture.id, 128).map { it.index })
            assertEquals(256L, repository.page(capture.id, 256).single().index)
            assertTrue(repository.page(capture.id, 257).isEmpty())
            val preview = repository.body(capture.id, repository.record(capture.id, 0).response!!.body)
            assertTrue(preview.truncated); assertEquals(1_048_576, preview.bytes.size)
            assertFailsWith<IllegalArgumentException> { repository.page(capture.id, 0, 129) }
        }
    }
    @Test fun `JSON escapes across streaming chunks roundtrip including surrogate pairs`() = runBlocking {
        fixture { root, repository ->
            val content = "x".repeat(8191) + "\n\t\"\\λ日本語\uD834\uDD1E"
            val input = root.resolve("escapes.har").apply { writeText(har(listOf(entry(responseText = content)))) }
            val capture = repository.open(input.path) {}
            val value = repository.record(capture.id, 0).response!!.body
            assertEquals(content, repository.body(capture.id, value).bytes.decodeToString())
        }
    }
    @Test fun `malformed HAR duplicate fields versions invalid encodings depth and native binaries roll back`() = runBlocking {
        fixture { root, repository ->
            val bad = listOf("not a capture", "{\"log\":{\"version\":\"2\",\"entries\":[]}}", "{\"log\":{\"version\":\"1.2\",\"entries\":[],\"entries\":[]}}",
                har(listOf(entry(responseText = "%%%", encoding = "base64"))),
                har(listOf(entry(responseText = "a"))).dropLast(2),
                "{\"log\":{\"version\":\"1.2\",\"entries\":[" + "[".repeat(65) + "0" + "]".repeat(65) + "]}}")
            for ((i, text) in bad.withIndex()) {
                val input = root.resolve("bad-$i.har").apply { writeText(text) }
                assertFails { repository.open(input.path) {} }
                assertTrue(root.resolve("private").listFiles().orEmpty().isEmpty(), "Failed imports leave no partial handles")
            }
        }
    }
    @Test fun `cancellation during parsing removes only task files and preserves previous imported history`() = runBlocking {
        fixture { root, repository ->
            val first = root.resolve("first.har").apply { writeText(har(listOf(entry()))) }
            val retained = repository.open(first.path) {}
            val second = root.resolve("second.har").apply { writeText(har(List(64) { entry() })) }
            assertFailsWith<CancellationException> { repository.open(second.path) { if (it.records >= 32) throw CancellationException("cancel test") } }
            assertEquals(1, repository.page(retained.id, 0).size)
            assertEquals(1, root.resolve("private").listFiles().orEmpty().size)
        }
    }
    @Test fun `Charles published XML shape supports headers text base64 and exact timings without fetching DTD`() = runBlocking {
        fixture { root, repository ->
            val input = root.resolve("charles.xml").apply { writeText("""<?xml version="1.0"?><!DOCTYPE charles-session SYSTEM "http://127.0.0.1:1/unreachable.dtd"><charles-session>
                <transaction method="POST" protocolVersion="HTTP/1.1" protocol="https" host="api.test" port="443" path="/graphql" query="x=1" startTimeMillis="1000" endTimeMillis="1125">
                <request headers="true" body="true"><headers><header><name>Content-Type</name><value>application/json</value></header></headers><body encoding="text">{"query":"{hello}"}</body></request>
                <response status="200" headers="false" body="true"><body encoding="base64">AAH/</body></response></transaction></charles-session>""".trimIndent()) }
            val capture = repository.open(input.path) {}
            assertEquals(CaptureImportFormat.CHARLES_XML, capture.format)
            val value = repository.record(capture.id, 0)
            assertEquals(125.0, value.durationMillis); assertEquals(1000L, value.startedAt)
            assertEquals("https://api.test:443/graphql?x=1", value.destination)
            assertContentEquals(byteArrayOf(0, 1, -1), repository.body(capture.id, value.response!!.body).bytes)
            assertEquals("{\"query\":\"{hello}\"}", repository.body(capture.id, value.request!!.body).bytes.decodeToString())
        }
    }
    @Test fun `XML external entities and incomplete sessions cannot expose files or survive rollback`() = runBlocking {
        fixture { root, repository ->
            val secret = root.resolve("secret").apply { writeText("must not appear") }
            val xml = """<!DOCTYPE charles-session [<!ENTITY evil SYSTEM "${secret.toURI()}">]><charles-session><transaction method="GET"><request headers="false" body="true"><body encoding="text">&evil;</body></request><response/></transaction></charles-session>"""
            val input = root.resolve("hostile.xml").apply { writeText(xml) }
            assertFails { repository.open(input.path) {} }
            assertEquals("must not appear", secret.readText()); assertTrue(root.resolve("private").listFiles().orEmpty().isEmpty())
        }
    }
    @Test fun `KNet archive preserves native records and verifies original payload bytes`() = runBlocking {
        fixture { root, repository ->
            val bytes = byteArrayOf(0, 1, 2, -1)
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val record = """{"kind":"http","id":"foreign","source":{"deviceId":"live-phone"},"request":{"method":"GET","url":"https://test/","headers":[],"body":{"state":"EMPTY"}},"response":{"status":200,"headers":[],"body":{"state":"COMPLETE","entry":"payloads/1.bin","storedBytes":4,"sha256":"$digest"}}}"""
            val file = root.resolve("capture.knetcapture")
            archive(file, mapOf("manifest.json" to """{"format":"KNet Capture","schemaVersion":1,"records":1}""".encodeToByteArray(),
                "records.jsonl" to (record + "\n" + """{"kind":"message","exchangeId":"foreign","protocol":"websocket","body":{"state":"UNAVAILABLE"}}""" + "\n").encodeToByteArray(), "payloads/1.bin" to bytes))
            val capture = repository.open(file.path) {}
            assertEquals(2L, capture.records)
            val first = repository.record(capture.id, 0)
            assertTrue(first.metadataJson.contains("live-phone"), "Source identity remains historical metadata only")
            assertContentEquals(bytes, repository.body(capture.id, first.response!!.body).bytes)
            assertEquals("message", repository.record(capture.id, 1).kind)
        }
    }
    @Test fun `archive traversal missing payload integrity and unsupported schemas are rejected atomically`() = runBlocking {
        fixture { root, repository ->
            val variants = listOf(
                mapOf("../escape" to byteArrayOf(1)),
                mapOf("manifest.json" to """{"format":"KNet Capture","schemaVersion":99}""".encodeToByteArray()),
                mapOf("manifest.json" to """{"format":"KNet Capture","schemaVersion":1}""".encodeToByteArray(), "records.jsonl" to """{"body":{"state":"COMPLETE","entry":"payloads/1.bin","sha256":"bad"}}""".encodeToByteArray(), "payloads/1.bin" to byteArrayOf(1)))
            variants.forEachIndexed { i, entries ->
                val file = root.resolve("bad-$i.zip"); archive(file, entries)
                assertFails { repository.open(file.path) {} }
                assertTrue(root.resolve("private").listFiles().orEmpty().isEmpty())
            }
            assertFalse(root.resolve("escape").exists())
        }
    }
    @Test fun `large payload streams to disk and sparse oversized files fail before parsing`() = runBlocking {
        fixture { root, repository ->
            val file = root.resolve("large-payload.har")
            val started = System.nanoTime()
            file.bufferedWriter().use { output ->
                output.write("{\"log\":{\"version\":\"1.2\",\"entries\":[{\"request\":{\"method\":\"GET\",\"url\":\"https://large.test/\",\"bodySize\":0},\"response\":{\"status\":200,\"content\":{\"text\":\"")
                repeat(512) { output.write("x".repeat(32768)) }
                output.write("\"}}}]}}")
            }
            val capture = repository.open(file.path) {}
            val record = repository.record(capture.id, 0)
            assertEquals(16L * 1024 * 1024, record.response!!.body.bytes)
            assertTrue(record.metadataJson.length < 2048, "Payload bytes must not leak back into metadata")
            assertTrue(repository.body(capture.id, record.response!!.body, 64).truncated)
            assertFailsWith<IllegalArgumentException> { repository.body(capture.id, record.response!!.body.copy(id = "../escape")) }
            println("Capture import 16 MiB streamed and indexed in ${(System.nanoTime() - started) / 1_000_000} ms")
            val oversized = root.resolve("oversized.har")
            java.io.RandomAccessFile(oversized, "rw").use { it.setLength(512L * 1024 * 1024 + 1) }
            assertFailsWith<IllegalArgumentException> { repository.open(oversized.path) {} }
        }
    }
    @Test fun `UTF8 BOM is accepted while invalid source bytes are rejected without replacement`() = runBlocking {
        fixture { root, repository ->
            val input = root.resolve("bom.har").apply { writeBytes(byteArrayOf(-17, -69, -65) + har(listOf(entry())).encodeToByteArray()) }
            val imported = repository.open(input.path) {}; assertEquals(1L, imported.records); repository.close(imported.id)
            input.writeBytes("{\"bad\":\"".encodeToByteArray() + byteArrayOf(-61, 40) + "\"}".encodeToByteArray())
            assertFails { repository.open(input.path) {} }
            assertTrue(root.resolve("private").listFiles().orEmpty().isEmpty())
        }
    }

    private suspend fun fixture(block: suspend (java.io.File, DesktopCaptureImportRepository) -> Unit) {
        val root = Files.createTempDirectory("knet-import-test").toFile()
        try { block(root, DesktopCaptureImportRepository(root.resolve("private"))) } finally { root.deleteRecursively() }
    }
    private fun har(entries: List<JsonObject>) = buildJsonObject { put("log", buildJsonObject { put("entries", JsonArray(entries)); put("version", "1.2") }) }.toString()
    private fun entry(responseText: String? = "{\"hello\":true}", encoding: String? = null, state: String? = null): JsonObject = buildJsonObject {
        put("startedDateTime", "2026-11-15T00:00:00Z"); put("time", 125.5)
        put("request", buildJsonObject { put("method", "GET"); put("url", "https://same.test/graphql?operationName=Q&x=1&x=2"); put("bodySize", 0)
            put("headers", buildJsonArray { for (value in listOf("a", "b")) add(buildJsonObject { put("name", "X-Test"); put("value", value) }) }) })
        put("response", buildJsonObject { put("status", 200); put("content", buildJsonObject { put("size", 20)
            responseText?.let { put("text", it) }; encoding?.let { put("encoding", it) }
            state?.let { put("_capture", buildJsonObject { put("state", it) }) }
        }) })
    }
    private fun archive(file: java.io.File, entries: Map<String, ByteArray>) = ZipOutputStream(file.outputStream()).use { zip ->
        entries.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
    }
}
