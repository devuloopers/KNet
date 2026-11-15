package com.devuloopers.knet.data.desktop.export

import androidx.room.deferredTransaction
import androidx.room.useReaderConnection
import com.devuloopers.knet.application.contract.export.*
import com.devuloopers.knet.application.contract.traffic.*
import com.devuloopers.knet.storage.database.KNetDatabase
import com.devuloopers.knet.traffic.id.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*
import java.io.*
import java.nio.file.*
import java.security.MessageDigest
import kotlin.time.Instant
import kotlin.io.encoding.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.uuid.Uuid

/**
 * Private on-disk snapshots, streamed archive writers and atomic destination publication.
 * SQLite freezes metadata before payload copying; the body's global export read lease fences deferred
 * deletion without delaying Clear's metadata transaction. Only preparation pins canonical bodies.
 */
class DesktopCaptureExportRepository(
    private val database: KNetDatabase?,
    private val traffic: TrafficQuery,
    private val messages: ProtocolMessageQuery,
    private val bodyStore: BodyStore,
    private val studio: DesktopStudioExportRecorder,
    root: File,
) : CaptureExportRepository {
    private val directory = root.apply { mkdirs(); setReadable(false, false); setReadable(true, true); setWritable(false, false); setWritable(true, true); setExecutable(false, false); setExecutable(true, true) }
    private val prepared = ConcurrentHashMap<String, File>()
    private val permits = Semaphore(2)

    init {
        // This root belongs exclusively to this process-owned adapter, never the user's destination.
        directory.listFiles()?.filter { it.isDirectory && it.name.startsWith("export-") }?.forEach { it.deleteRecursively() }
    }

    override suspend fun prepare(source: CaptureExportSource): PreparedCaptureExport {
        var owned: Pair<String, File>? = null
        try { return withContext(Dispatchers.IO) {
        permits.withPermit {
            val id = Uuid.random().toString()
            val folder = File(directory, "export-$id").apply { mkdirs(); setReadable(false, false); setReadable(true, true); setWritable(false, false); setWritable(true, true); setExecutable(false, false); setExecutable(true, true) }
            owned = id to folder
            try {
                val timestamp = System.currentTimeMillis()
                var records = 0L; var http = 0L; var framed = 0L; var missing = 0L; var unavailable = 0L
                if (source is CaptureExportSource.Studio) {
                    studio.snapshot(source.id, folder)
                } else bodyStore.retainExportReads().use {
                    val metadata = File(folder, "metadata.jsonl")
                    metadata.bufferedWriter().use { writer ->
                        suspend fun emit(value: JsonObject) {
                            currentCoroutineContext().ensureActive()
                            val metadata = value.toMutableMap()
                            val source = value["source"]?.jsonObject
                            val deviceId = source?.get("deviceId")?.jsonPrimitive?.content
                            if (deviceId != null) database?.registeredDeviceDao()?.getRegisteredDevice(deviceId)?.let { device ->
                                metadata["source"] = JsonObject(source + ("deviceName" to JsonPrimitive(device.displayName)))
                            }
                            writer.appendLine(JsonObject(metadata).toString())
                        }
                        suspend fun exchange(value: com.devuloopers.knet.traffic.model.HttpExchangeSnapshot) {
                            val metadata = ExportJson.exchange(value, value.connectionId?.let { traffic.getConnectionIngress(it) }).toMutableMap()
                            database?.canonicalCaptureDao()?.getInspectionAnnotations(value.id.value)?.let { annotations ->
                                metadata["annotations"] = JsonArray(annotations.map { annotation -> ExportJson.obj {
                                    put("inspector", annotation.inspectorId); put("schemaVersion", annotation.version); put("state", annotation.state)
                                    annotation.payloadEncoded?.let { put("document", runCatching { ExportJson.json.parseToJsonElement(it) }.getOrElse { JsonPrimitive("Unavailable legacy annotation") }) }
                                    annotation.errorCode?.let { put("errorCode", it) }
                                } })
                            }
                            emit(JsonObject(metadata))
                            var cursor: ProtocolMessagePageCursor? = null
                            do {
                                val page = messages.queryMessages(ProtocolMessagePageQuery(value.id, cursor, 128))
                                page.items.forEach { emit(ExportJson.message(it)) }
                                cursor = page.nextCursor
                            } while (cursor != null)
                        }
                        suspend fun collect() {
                            when (source) {
                                is CaptureExportSource.Selection -> source.records.forEach { item ->
                                    currentCoroutineContext().ensureActive()
                                    when (item) {
                                        is TrafficRecordId.Exchange -> {
                                            val value = traffic.getExchange(item.id)
                                            if (value == null) unavailable++ else {
                                                val ingress = value.connectionId?.let { traffic.getConnectionIngress(it) }
                                                require(source.source == TrafficSource.All || TrafficSource.fromIngress(ingress?.ingress) == source.source) { "A selected item belongs to another traffic source." }
                                                exchange(value)
                                            }
                                        }
                                        is TrafficRecordId.Opaque -> {
                                            val value = traffic.getOpaqueFlow(item.id)
                                            if (value == null) unavailable++ else {
                                                val ingress = traffic.getConnectionIngress(value.connectionId)
                                                require(source.source == TrafficSource.All || TrafficSource.fromIngress(ingress?.ingress) == source.source) { "A selected connection belongs to another traffic source." }
                                                emit(ExportJson.opaque(value, ingress))
                                            }
                                        }
                                    }
                                }
                                is CaptureExportSource.Item -> when (val item = source.id) {
                                    is TrafficRecordId.Exchange -> exchange(requireNotNull(traffic.getExchange(item.id)) { "This exchange was cleared before export preparation." })
                                    is TrafficRecordId.Opaque -> {
                                        val value = requireNotNull(traffic.getOpaqueFlow(item.id)) { "This connection was cleared before export preparation." }
                                        emit(ExportJson.opaque(value, traffic.getConnectionIngress(value.connectionId)))
                                    }
                                }
                                is CaptureExportSource.Traffic -> {
                                    var cursor: TrafficPageCursor? = null
                                    do {
                                        val page = traffic.query(source.query.copy(cursor = cursor, limit = 128, arrivalsAfter = null, seenThrough = null,
                                            newerThan = null, olderThan = null))
                                        val ordered = buildList<Pair<TrafficReadPoint, suspend () -> Unit>> {
                                            page.items.forEach { item -> add(item.readPoint to { exchange(item.exchange) }) }
                                            page.opaqueItems.forEach { item -> add(item.readPoint to { emit(ExportJson.opaque(item.flow, traffic.getConnectionIngress(item.flow.connectionId))) }) }
                                        }.sortedWith(if (source.query.direction == TrafficSortDirection.NEWEST_FIRST)
                                            compareByDescending { it.first } else compareBy { it.first })
                                        ordered.forEach { it.second() }
                                        cursor = page.nextCursor
                                    } while (cursor != null)
                                }
                                is CaptureExportSource.Studio -> error("Studio snapshots have independent ownership.")
                            }
                        }
                        if (database == null) collect() else {
                            val owner = currentCoroutineContext().job
                            database.useReaderConnection { connection -> withContext(owner) { connection.deferredTransaction { collect() } } }
                        }
                    }
                    var payloadSequence = 0L
                    File(folder, "records.jsonl").bufferedWriter().use { output ->
                        metadata.useLines { lines ->
                            // useLines is inline: suspending bounded reads remain confined to this IO coroutine.
                            for (line in lines) {
                                currentCoroutineContext().ensureActive()
                                suspend fun copyPayloads(value: JsonElement): JsonElement = when (value) {
                                    is JsonArray -> JsonArray(value.map { copyPayloads(it) })
                                    is JsonObject -> if (value.containsKey("_bodyId")) {
                                        val entry = "payloads/${++payloadSequence}.bin"
                                        val destination = File(folder, entry).apply { parentFile.mkdirs() }
                                        val count = value["storedBytes"]!!.jsonPrimitive.long
                                        val digest = MessageDigest.getInstance("SHA-256")
                                        val result = try {
                                            destination.outputStream().use { bodyOutput ->
                                                var offset = 0L
                                                while (offset < count) {
                                                    currentCoroutineContext().ensureActive()
                                                    val chunk = bodyStore.readBody(BodyId(value["_bodyId"]!!.jsonPrimitive.content), BodyRange(offset, minOf(65_536L, count - offset).toInt()))
                                                    check(chunk.offset == offset && chunk.size > 0 && chunk.size <= count - offset) { "Stored payload ended before its captured length." }
                                                    val bytes = chunk.copyBytes(); bodyOutput.write(bytes); digest.update(bytes); offset += bytes.size
                                                }
                                            }
                                            val actual = digest.digest().joinToString("") { "%02x".format(it) }
                                            check(value["sha256"]?.jsonPrimitive?.content?.equals(actual, true) != false) { "Stored payload integrity check failed." }
                                            JsonObject(value.filterKeys { it != "_bodyId" } + mapOf("entry" to JsonPrimitive(entry), "sha256" to JsonPrimitive(actual)))
                                        } catch (cancelled: CancellationException) { throw cancelled }
                                        catch (_: Exception) {
                                            destination.delete()
                                            JsonObject(value.filterKeys { it !in setOf("_bodyId", "sha256") } + mapOf("state" to JsonPrimitive("MISSING")))
                                        }
                                        result
                                    } else JsonObject(value.mapValues { copyPayloads(it.value) })
                                    else -> value
                                }
                                output.appendLine(copyPayloads(ExportJson.json.parseToJsonElement(line)).toString())
                            }
                        }
                    }
                    metadata.delete()
                }
                val context = currentCoroutineContext()
                File(folder, "records.jsonl").forEachLine { line ->
                    context.ensureActive()
                    val value = ExportJson.json.parseToJsonElement(line).jsonObject
                    when (value["kind"]?.jsonPrimitive?.content) { "http" -> { http++; records++ }; "message" -> framed++; "event" -> Unit; else -> records++ }
                    fun countMissing(element: JsonElement) {
                        when (element) {
                            is JsonObject -> { if (element["state"]?.jsonPrimitive?.content in setOf("MISSING", "UNAVAILABLE")) missing++; element.values.forEach(::countMissing) }
                            is JsonArray -> element.forEach(::countMissing)
                            else -> Unit
                        }
                    }
                    countMissing(value)
                }
                check(records > 0) { "There is no retained traffic to export." }
                val result = PreparedCaptureExport(id, records, http, framed, missing, timestamp, unavailable)
                prepared[id] = folder
                result
            } catch (failure: Throwable) { folder.deleteRecursively(); throw failure }
        }
        } } catch (failure: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) { owned?.let { (id, folder) -> prepared.remove(id); folder.deleteRecursively() } }
            throw failure
        }
    }

    override suspend fun release(prepared: PreparedCaptureExport): Unit = withContext(Dispatchers.IO) {
        this@DesktopCaptureExportRepository.prepared.remove(prepared.id)?.deleteRecursively(); Unit
    }

    override suspend fun write(prepared: PreparedCaptureExport, options: CaptureExportOptions, destination: String,
        progress: (CaptureExportProgress) -> Unit): Unit = withContext(Dispatchers.IO) {
        permits.withPermit {
            val folder = requireNotNull(this@DesktopCaptureExportRepository.prepared[prepared.id]) { "This export has expired." }
            require(options.format != CaptureExportFormat.HAR || prepared.httpExchanges > 0) { "No HAR-compatible exchanges." }
            val target = File(destination).absoluteFile
            require(!target.toPath().normalize().startsWith(directory.toPath().toAbsolutePath().normalize())) { "Choose a destination outside private export storage." }
            val temporary = Files.createTempFile(target.parentFile.toPath(), ".knet-export-", ".tmp").toFile()
            temporary.setReadable(false, false); temporary.setReadable(true, true); temporary.setWritable(false, false); temporary.setWritable(true, true)
            try {
                FileOutputStream(temporary).use { file ->
                    val buffered = BufferedOutputStream(file, 65_536)
                    if (options.format == CaptureExportFormat.KNET) writeNative(buffered, folder, prepared, options, progress)
                    else writeHar(buffered, folder, options, progress)
                    buffered.flush()
                    file.fd.sync()
                }
                currentCoroutineContext().ensureActive()
                // Never silently fall back to a partial copy on filesystems without atomic rename.
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally { temporary.delete() }
        }
    }

    private suspend fun writeNative(output: OutputStream, folder: File, prepared: PreparedCaptureExport,
        options: CaptureExportOptions, progress: (CaptureExportProgress) -> Unit) {
        val context = currentCoroutineContext()
        ZipOutputStream(NonClosingOutput(output)).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(ExportJson.obj {
                put("format", "KNet Capture"); put("schemaVersion", 1); put("producer", "KNet")
                put("producerVersion", com.devuloopers.knet.domain.config.AppMetadata.APP_VERSION)
                put("snapshotEpochMillis", prepared.snapshotEpochMillis); put("records", prepared.records)
                put("httpExchanges", prepared.httpExchanges); put("messages", prepared.messages)
                put("unavailableSelectedRecords", prepared.unavailableRecords)
                put("recordsEntry", "records.jsonl"); put("payloadsIncluded", options.includePayloads)
                put("headerCredentialsIncluded", options.includeCredentials)
                put("privacyNotice", "Header/cookie removal does not scrub arbitrary query or payload secrets.")
            }.toString().encodeToByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("records.jsonl"))
            var count = 0L
            File(folder, "records.jsonl").useLines { lines -> for (line in lines) {
                context.ensureActive()
                zip.write((transform(ExportJson.json.parseToJsonElement(line), options).toString() + "\n").encodeToByteArray())
                progress(CaptureExportProgress(++count, 0))
            } }
            zip.closeEntry()
            if (options.includePayloads) {
                var bytes = 0L
                val payloadDirectory = File(folder, "payloads")
                if (payloadDirectory.isDirectory) Files.newDirectoryStream(payloadDirectory.toPath()).use { paths -> paths.forEach { path ->
                    val payload = path.toFile()
                    context.ensureActive(); zip.putNextEntry(ZipEntry("payloads/${payload.name}"))
                    payload.inputStream().use { input -> val buffer = ByteArray(65_536)
                        while (true) { context.ensureActive(); val read = input.read(buffer); if (read < 0) break
                            zip.write(buffer, 0, read); bytes += read; progress(CaptureExportProgress(count, bytes)) }
                    }; zip.closeEntry()
                } }
            }
        }
    }

    private suspend fun writeHar(output: OutputStream, folder: File, options: CaptureExportOptions, progress: (CaptureExportProgress) -> Unit) {
        val context = currentCoroutineContext()
        fun text(value: String) = output.write(value.encodeToByteArray())
        text("{\"log\":{\"version\":\"1.2\",\"creator\":{\"name\":\"KNet\",\"version\":${JsonPrimitive(com.devuloopers.knet.domain.config.AppMetadata.APP_VERSION)}},\"entries\":[")
        var count = 0L; var bytes = 0L
        File(folder, "records.jsonl").useLines { lines -> for (line in lines) {
            context.ensureActive()
            val entry = transform(ExportJson.json.parseToJsonElement(line), options).jsonObject
            if (entry["kind"]?.jsonPrimitive?.content != "http") continue
            if (count > 0) text(",")
            val request = entry["request"]!!.jsonObject
            val response = entry["response"]?.jsonObject
            val total = entry["timings"]?.jsonObject?.get("total")?.jsonPrimitive?.longOrNull ?: -1
            val har = ExportJson.obj {
                put("startedDateTime", Instant.fromEpochMilliseconds(entry["startedAt"]!!.jsonPrimitive.long).toString())
                put("time", total); put("request", ExportJson.obj {
                    put("method", request["method"]!!); put("url", request["url"]!!); put("httpVersion", request["protocol"] ?: JsonPrimitive(""))
                    put("headers", request["headers"]!!); put("cookies", cookies(request, response = false, options.includeCredentials))
                    request["body"]?.let { put("_capture", it) }
                    val uri = runCatching { java.net.URI(request["url"]!!.jsonPrimitive.content) }.getOrNull()
                    put("queryString", ExportJson.pairs(uri?.rawQuery?.split('&')?.map { part ->
                        java.net.URLDecoder.decode(part.substringBefore('='), "UTF-8") to java.net.URLDecoder.decode(part.substringAfter('=', ""), "UTF-8")
                    }.orEmpty()))
                    put("headersSize", -1); put("bodySize", request["body"]?.jsonObject?.get("observedBytes") ?: JsonPrimitive(-1))
                })
                put("response", ExportJson.obj {
                    put("status", response?.get("status") ?: JsonPrimitive(0)); put("statusText", response?.get("statusText") ?: JsonPrimitive(""))
                    put("httpVersion", response?.get("protocol") ?: JsonPrimitive("")); put("headers", response?.get("headers") ?: JsonArray(emptyList()))
                    put("cookies", cookies(response, response = true, options.includeCredentials)); put("redirectURL", header(response, "location"))
                    put("headersSize", -1); put("bodySize", response?.get("body")?.jsonObject?.get("observedBytes") ?: JsonPrimitive(-1))
                })
                put("cache", ExportJson.obj { }); put("timings", ExportJson.obj { put("send", -1); put("wait", -1); put("receive", -1) })
                put("_knet", JsonObject(entry.filterKeys { it !in setOf("request", "response") }))
            }.toMutableMap()
            fun utf8(body: JsonObject?): Boolean {
                val entryName = body?.get("entry")?.jsonPrimitive?.content ?: return false
                return try {
                    val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    java.io.InputStreamReader(File(folder, entryName).inputStream(), decoder).use { strict ->
                        val buffer = CharArray(8192); while (strict.read(buffer) >= 0) context.ensureActive()
                    }; true
                } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { false }
            }
            fun payloadObject(body: JsonObject?, contentType: String, responseBody: Boolean): JsonObject = ExportJson.obj {
                put("mimeType", contentType.ifBlank { "application/octet-stream" })
                if (responseBody) put("size", body?.get("observedBytes") ?: JsonPrimitive(0))
                put("_capture", body ?: ExportJson.obj { put("state", "UNAVAILABLE") })
                if (body?.containsKey("entry") == true) {
                    put("text", "")
                    if (responseBody) put("encoding", "base64") else if (!utf8(body)) put("_encoding", "base64")
                }
            }
            val req = har["request"]!!.jsonObject.toMutableMap()
            val reqBody = request["body"]?.jsonObject
            if (reqBody?.get("entry") != null) req["postData"] = payloadObject(reqBody, header(request, "content-type"), false)
            har["request"] = JsonObject(req)
            val res = har["response"]!!.jsonObject.toMutableMap()
            res["content"] = payloadObject(response?.get("body")?.jsonObject, header(response, "content-type"), true)
            har["response"] = JsonObject(res)
            fun stream(value: JsonElement) {
                context.ensureActive()
                when (value) {
                    is JsonObject -> {
                        text("{")
                        value.entries.forEachIndexed { index, (key, child) ->
                            if (index > 0) text(",")
                            text(JsonPrimitive(key).toString()); text(":")
                            val payload = value["_capture"]?.jsonObject?.get("entry")?.jsonPrimitive?.content
                            if (key == "text" && payload != null) {
                                text("\"")
                                if (value["encoding"]?.jsonPrimitive?.content == "base64" || value["_encoding"]?.jsonPrimitive?.content == "base64") {
                                    File(folder, payload).inputStream().use { input ->
                                        val buffer = ByteArray(65_538)
                                        var pending = 0
                                        while (true) {
                                            context.ensureActive()
                                            val read = input.read(buffer, pending, 65_535 - pending)
                                            if (read < 0) { if (pending > 0) output.write(Base64.encodeToByteArray(buffer, 0, pending)); break }
                                            val total = pending + read
                                            val aligned = total - total % 3
                                            if (aligned > 0) output.write(Base64.encodeToByteArray(buffer, 0, aligned))
                                            pending = total - aligned
                                            if (pending > 0) buffer.copyInto(buffer, 0, aligned, total)
                                            bytes += read; progress(CaptureExportProgress(count, bytes))
                                        }
                                    }
                                } else File(folder, payload).inputStream().use { input ->
                                    val buffer = ByteArray(65_536)
                                    while (true) {
                                        context.ensureActive(); val read = input.read(buffer); if (read < 0) break
                                        var start = 0
                                        for (index in 0 until read) {
                                            val byte = buffer[index].toInt() and 255
                                            if (byte == 34 || byte == 92 || byte < 32) {
                                                output.write(buffer, start, index - start)
                                                text(when (byte) { 34 -> "\\\""; 92 -> "\\\\"; else -> "\\u%04x".format(byte) })
                                                start = index + 1
                                            }
                                        }
                                        output.write(buffer, start, read - start); bytes += read; progress(CaptureExportProgress(count, bytes))
                                    }
                                }
                                text("\"")
                            } else if (key == "_capture" && child is JsonObject) {
                                // HAR embeds bytes in text; private spool paths are not HAR attachments.
                                stream(JsonObject(child.filterKeys { it != "entry" }))
                            } else stream(child)
                        }
                        text("}")
                    }
                    is JsonArray -> { text("["); value.forEachIndexed { index, child -> if (index > 0) text(","); stream(child) }; text("]") }
                    else -> text(value.toString())
                }
            }
            stream(JsonObject(har))
            progress(CaptureExportProgress(++count, bytes))
        } }
        text("]}}")
    }

    private fun header(value: JsonObject?, name: String): String = value?.get("headers")?.jsonArray
        ?.firstOrNull { it.jsonObject["name"]?.jsonPrimitive?.content.equals(name, true) }
        ?.jsonObject?.get("value")?.jsonPrimitive?.content.orEmpty()

    /** Cookie projections share the header privacy policy; original attributes remain in ordered headers. */
    private fun cookies(head: JsonObject?, response: Boolean, includeCredentials: Boolean): JsonArray {
        if (!includeCredentials) return JsonArray(emptyList())
        val headerName = if (response) "set-cookie" else "cookie"
        return buildJsonArray {
            head?.get("headers")?.jsonArray?.forEach { field ->
                val value = field.jsonObject
                if (!value["name"]?.jsonPrimitive?.content.equals(headerName, true)) return@forEach
                val raw = value["value"]?.jsonPrimitive?.content.orEmpty()
                if (response) {
                    // Do not split Set-Cookie on commas: Expires dates can contain them.
                    runCatching { java.net.HttpCookie.parse(raw) }.getOrDefault(emptyList()).forEach { cookie -> add(ExportJson.obj {
                        put("name", cookie.name); put("value", cookie.value)
                        cookie.path?.let { put("path", it) }; cookie.domain?.let { put("domain", it) }
                        put("secure", cookie.secure); put("httpOnly", cookie.isHttpOnly)
                    }) }
                } else raw.split(';').forEach { part ->
                    val name = part.substringBefore('=').trim()
                    if ('=' in part && name.isNotEmpty()) add(ExportJson.obj {
                        put("name", name); put("value", part.substringAfter('=').trim())
                    })
                }
            }
        }
    }

    private fun transform(value: JsonElement, options: CaptureExportOptions): JsonElement = when (value) {
        is JsonArray -> JsonArray(value.map { transform(it, options) })
        is JsonObject -> {
            val fields = value.toMutableMap()
            if (!options.includeCredentials && fields["name"]?.jsonPrimitive?.content?.lowercase() in CREDENTIAL_HEADERS) {
                fields["value"] = JsonPrimitive("[REDACTED]")
            }
            if (!options.includePayloads && fields.containsKey("entry")) {
                fields.remove("entry"); fields.remove("sha256"); fields["state"] = JsonPrimitive("OMITTED_BY_USER")
            }
            JsonObject(fields.mapValues { transform(it.value, options) })
        }
        else -> value
    }

    private class NonClosingOutput(output: OutputStream) : FilterOutputStream(output) {
        override fun write(bytes: ByteArray, offset: Int, length: Int) { out.write(bytes, offset, length) }
        override fun write(bytes: ByteArray) { out.write(bytes) }
        override fun close() { flush() }
    }
    private companion object {
        val CREDENTIAL_HEADERS = setOf("authorization", "proxy-authorization", "cookie", "set-cookie", "x-api-key")
    }
}
