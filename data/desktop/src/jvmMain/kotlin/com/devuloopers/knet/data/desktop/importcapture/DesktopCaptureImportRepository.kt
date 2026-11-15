package com.devuloopers.knet.data.desktop.importcapture

import com.devuloopers.knet.application.contract.importcapture.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.io.*
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.time.Instant
import kotlin.io.encoding.Base64
import kotlin.io.encoding.decodingWith
import java.util.zip.ZipFile
import kotlin.uuid.Uuid

/** Disk-indexed, process-owned historical imports. Parsing never writes into live Room, devices or policies. */
class DesktopCaptureImportRepository(private val directory: File) : CaptureImportRepository {
    private val mutex = Mutex()
    private val handles = mutableMapOf<String, Pair<ImportedCapture, File>>()
    private val parsing = Mutex()
    init {
        directory.mkdirs(); directory.setReadable(false, false); directory.setReadable(true, true)
        directory.setWritable(false, false); directory.setWritable(true, true); directory.setExecutable(false, false); directory.setExecutable(true, true)
        // Only this dedicated process-spool directory is retired; source files and live history are independent.
        directory.listFiles()?.filter { it.name.startsWith("import-") }?.forEach { it.deleteRecursively() }
    }

    override suspend fun open(path: String, progress: (CaptureImportProgress) -> Unit): ImportedCapture = parsing.withLock {
        withContext(Dispatchers.IO) {
            require(mutex.withLock { handles.size < 8 }) { "Close an imported capture before opening another (maximum eight)." }
            val source = File(path)
            require(source.isFile && source.length() in 1..MAX_FILE_BYTES) { "Choose a local capture file up to 512 MiB." }
            val id = Uuid.random().toString()
            val folder = File(directory, "import-$id").apply { mkdir() }
            try {
                val context = currentCoroutineContext()
                val spool = ImportSpool(folder, { context.ensureActive() }, progress)
                val format = source.inputStream().buffered().use { input ->
                    var first = input.read()
                    if (first == 0xEF) { require(input.read() == 0xBB && input.read() == 0xBF); first = input.read() }
                    while (first == 32 || first == 9 || first == 10 || first == 13) first = input.read()
                    when (first) { 123 -> CaptureImportFormat.HAR; 60 -> CaptureImportFormat.CHARLES_XML; 80 -> CaptureImportFormat.KNET
                        else -> error("Unsupported capture. Export HAR from HTTP Toolkit, or HAR/XML from Charles. Native binary session formats require conversion.") }
                }
                spool.use {
                    when (format) {
                        CaptureImportFormat.HAR -> InputStreamReader(source.inputStream(), Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)).buffered().use { reader ->
                            StreamingCaptureJson(java.io.PushbackReader(reader, 1).apply { val first = read(); if (first >= 0 && first != 0xFEFF) unread(first) }) { context.ensureActive() }.har(spool::har) { spool.temporaryPayload() }
                        }
                        CaptureImportFormat.CHARLES_XML -> CharlesCaptureXml.read(source, spool)
                        CaptureImportFormat.KNET -> readArchive(source, spool)
                    }
                }
                require(spool.records > 0) { "The capture contains no records." }
                val capture = ImportedCapture(id, source.name.take(256), format, spool.records, spool.unavailable,
                    buildList { add("Imported history only. Recorded device IDs and rules are not activated.")
                        if (format != CaptureImportFormat.KNET) add("HTTP interchange may omit protocol messages, encrypted flows or original wire encoding.")
                        if (spool.unavailable > 0) add("${spool.unavailable} payloads are unavailable or incomplete in the source.") })
                mutex.withLock { handles[id] = capture to folder }; capture
            } catch (failure: Throwable) { folder.deleteRecursively(); throw failure }
        }
    }

    override suspend fun page(id: String, offset: Long, limit: Int): List<ImportedRecord> = readPage(id, offset, limit, false)

    override suspend fun record(id: String, index: Long): ImportedRecord = readPage(id, index, 1, true).single()

    private suspend fun readPage(id: String, offset: Long, limit: Int, details: Boolean): List<ImportedRecord> = withContext(Dispatchers.IO) {
        require(offset >= 0 && limit in 1..128)
        mutex.withLock {
            val (capture, folder) = requireNotNull(handles[id]) { "This imported capture is closed." }
            if (offset >= capture.records) return@withLock emptyList()
            RandomAccessFile(File(folder, "index"), "r").use { index -> RandomAccessFile(File(folder, "metadata"), "r").use { data ->
                index.seek(offset * 12)
                buildList {
                    repeat(minOf(limit.toLong(), capture.records - offset).toInt()) { number ->
                        currentCoroutineContext().ensureActive()
                        val position = index.readLong(); val size = index.readInt(); require(size in 1..MAX_METADATA_BYTES)
                        val bytes = ByteArray(size); data.seek(position); data.readFully(bytes)
                        add(decodeRecord(offset + number, Json.parseToJsonElement(bytes.decodeToString()).jsonObject, details))
                    }
                }
            } }
        }
    }

    override suspend fun body(id: String, body: ImportedBody, limit: Int): ImportedBodyPreview = withContext(Dispatchers.IO) {
        require(limit in 1..4_194_304)
        mutex.withLock {
            val folder = requireNotNull(handles[id]) { "This imported capture is closed." }.second
            val key = body.id ?: return@withLock ImportedBodyPreview(byteArrayOf(), false)
            require(key.matches(Regex("body-[0-9]+")))
            val file = File(folder, key); require(file.isFile) { "Imported payload is unavailable." }
            val bytes = file.inputStream().use { it.readNBytes(limit) }
            ImportedBodyPreview(bytes, file.length() > bytes.size)
        }
    }

    override suspend fun close(id: String): Unit = withContext(Dispatchers.IO) {
        mutex.withLock { handles.remove(id)?.second?.deleteRecursively() }; Unit
    }

    private fun readArchive(source: File, spool: ImportSpool) = ZipFile(source).use { zip ->
        val names = hashSetOf<String>(); var count = 0
        for (entry in zip.entries()) {
            spool.checkActive()
            require(++count <= 300_010 && names.add(entry.name)) { "Duplicate or excessive archive entries." }
            require(entry.name.isNotEmpty() && !entry.name.startsWith('/') && '\\' !in entry.name && ':' !in entry.name && entry.name.split('/').none { it == ".." || it == "." }) { "Unsafe archive entry name." }
            require(entry.size <= MAX_ARCHIVE_BYTES) { "Archive entry exceeds its size limit." }
        }
        val manifestEntry = requireNotNull(zip.getEntry("manifest.json")) { "Not a KNet capture archive. Convert native tool sessions to HAR/XML." }
        val manifest = zip.getInputStream(manifestEntry).use { input ->
            val bytes = input.readNBytes(65_537); require(bytes.size <= 65_536); Json.parseToJsonElement(bytes.decodeToString(throwOnInvalidSequence = true)).jsonObject
        }
        require(manifest.text("format") == "KNet Capture" && manifest["schemaVersion"]?.jsonPrimitive?.intOrNull == 1) { "Unsupported KNet capture format or schema." }
        val records = requireNotNull(zip.getEntry("records.jsonl")) { "Missing archive records." }
        InputStreamReader(zip.getInputStream(records), Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)).buffered().use { reader ->
            while (true) {
                val line = boundedLine(reader) ?: break
                require(line.isNotBlank()) { "Empty archive record." }
                val value = Json.parseToJsonElement(line).jsonObject
                fun copyBodies(element: JsonElement, depth: Int = 0): JsonElement {
                    spool.checkActive(); require(depth < 64)
                    return when (element) {
                        is JsonArray -> JsonArray(element.map { copyBodies(it, depth + 1) })
                        is JsonObject -> if (element["entry"] != null && element["state"] != null) {
                            val name = element.text("entry"); require(name?.matches(Regex("payloads/[0-9]+\\.bin")) == true) { "Invalid payload reference." }
                            val entry = requireNotNull(zip.getEntry(name)) { "Missing referenced archive payload." }
                            val body = zip.getInputStream(entry).use { spool.payload(it, element.text("state") ?: "UNAVAILABLE", false, element.text("sha256")) }
                            require(element["storedBytes"]?.jsonPrimitive?.longOrNull == null || element["storedBytes"]!!.jsonPrimitive.long == body["storedBytes"]!!.jsonPrimitive.long) { "Payload length mismatch." }
                            JsonObject(element.filterKeys { it != "entry" } + body)
                        } else JsonObject(element.mapValues { copyBodies(it.value, depth + 1) })
                        else -> element
                    }
                }
                spool.record(copyBodies(value).jsonObject)
            }
        }
        manifest["records"]?.jsonPrimitive?.longOrNull?.let { require(it == spool.parentRecords) { "Archive record count mismatch." } }
    }

    private fun decodeRecord(index: Long, value: JsonObject, details: Boolean): ImportedRecord {
        fun message(element: JsonElement?): ImportedMessage? = (element as? JsonObject)?.let { item ->
            val body = item["body"] as? JsonObject
            ImportedMessage((item["headers"] as? JsonArray).orEmpty().map { it.jsonObject.text("name").orEmpty() to it.jsonObject.text("value").orEmpty() },
                ImportedBody(body?.text("localBody"), body?.text("state")?.let { state -> ImportedBodyState.entries.firstOrNull { it.name == if (state == "OMITTED_BY_USER") "OMITTED" else state } } ?: ImportedBodyState.UNAVAILABLE,
                    body?.get("storedBytes")?.jsonPrimitive?.longOrNull ?: 0, body?.get("decoded")?.jsonPrimitive?.booleanOrNull == true, body?.get("utf8Text")?.jsonPrimitive?.booleanOrNull == true))
        }
        val request = value["request"] as? JsonObject; val response = value["response"] as? JsonObject
        return ImportedRecord(index, value.text("kind") ?: "unknown", request?.text("method"), request?.text("url") ?: value.text("host") ?: value.text("protocol") ?: "Recorded event",
            response?.get("status")?.jsonPrimitive?.intOrNull, value["startedAt"]?.jsonPrimitive?.longOrNull,
            (value["timings"] as? JsonObject)?.get("total")?.jsonPrimitive?.doubleOrNull, if (details) message(request) else null, if (details) message(response) else null,
            if (details) prettyJson.encodeToString(JsonObject.serializer(), value) else "",
            if (details && value["body"] is JsonObject) message(buildJsonObject { put("body", value["body"]!!) }) else null)
    }

    private companion object {
        val prettyJson = Json { prettyPrint = true }
        const val MAX_FILE_BYTES = 512L * 1024 * 1024
        const val MAX_ARCHIVE_BYTES = 1024L * 1024 * 1024
        const val MAX_METADATA_BYTES = 2 * 1024 * 1024
        fun boundedLine(reader: Reader): String? {
            val value = StringBuilder(); var next = reader.read()
            if (next < 0) return null
            while (next >= 0 && next != 10) { require(value.length < MAX_METADATA_BYTES) { "Archive metadata record exceeds two mebibytes." }; value.append(next.toChar()); next = reader.read() }
            return value.toString().removeSuffix("\r")
        }
    }
}

/** Task-owned bounded payload/metadata writer shared by the three import decoders. */
internal class ImportSpool(val folder: File, val checkActive: () -> Unit, private val progress: (CaptureImportProgress) -> Unit) : AutoCloseable {
    private val metadata = RandomAccessFile(File(folder, "metadata"), "rw")
    private val index = DataOutputStream(File(folder, "index").outputStream().buffered())
    private var sequence = 0L
    var parentRecords = 0L; private set
    var records = 0L; private set
    var unavailable = 0L; private set
    private var totalBytes = 0L
    fun temporaryPayload(): File = File.createTempFile("payload-", ".tmp", folder)
    fun payload(input: InputStream, state: String, decoded: Boolean, expectedDigest: String? = null, utf8Text: Boolean = false): JsonObject {
        val file = File(folder, "body-${++sequence}"); val digest = MessageDigest.getInstance("SHA-256"); var size = 0L
        file.outputStream().buffered().use { output ->
            val buffer = ByteArray(65_536)
            while (true) {
                checkActive(); val count = input.read(buffer); if (count < 0) break
                size += count; totalBytes += count
                require(size <= 64L * 1024 * 1024 && totalBytes <= 1024L * 1024 * 1024) { "Capture payload limits exceeded (64 MiB per body, 1 GiB total)." }
                output.write(buffer, 0, count); digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        require(expectedDigest == null || expectedDigest.equals(actual, true)) { "Capture payload integrity mismatch." }
        return buildJsonObject { put("localBody", file.name); put("state", state); put("storedBytes", size); put("decoded", decoded); put("utf8Text", utf8Text) }
    }
    fun record(value: JsonObject) {
        checkActive()
        if (value.text("kind") == "http") {
            val request = requireNotNull(value["request"] as? JsonObject) { "Captured HTTP request is missing." }
            require(!request.text("method").isNullOrBlank()) { "Captured method is missing." }
            val uri = java.net.URI(requireNotNull(request.text("url")))
            require(uri.scheme?.lowercase() in setOf("http", "https", "ws", "wss") && uri.host != null) { "Unsupported captured HTTP URL." }
        }
        for (item in listOfNotNull(value["request"] as? JsonObject, value["response"] as? JsonObject)) {
            item["headers"]?.let { element ->
                require(element is JsonArray) { "Capture headers must be ordered name/value pairs." }
                element.forEach { pair -> require(pair is JsonObject && pair["name"] is JsonPrimitive && pair["value"] is JsonPrimitive && pair["name"]!!.jsonPrimitive.isString && pair["value"]!!.jsonPrimitive.isString) { "Malformed captured header." } }
            }
        }
        require(records < 100_000) { "Capture exceeds 100,000 records." }
        fun count(element: JsonElement) {
            when (element) {
                is JsonObject -> { if (element.text("state") in setOf("UNAVAILABLE", "TRUNCATED", "REDACTED", "OMITTED", "OMITTED_BY_USER", "MISSING", "UNSUPPORTED")) unavailable++; element.values.forEach(::count) }
                is JsonArray -> element.forEach(::count)
                else -> Unit
            }
        }
        value.filterKeys { !it.startsWith("original") }.values.forEach(::count)
        if (value.text("kind") !in setOf("message", "event")) parentRecords++
        val bytes = value.toString().encodeToByteArray(); require(bytes.size <= 2_097_152) { "Capture record metadata exceeds two mebibytes." }
        index.writeLong(metadata.filePointer); index.writeInt(bytes.size); metadata.write(bytes); records++
        if (records % 32L == 0L) progress(CaptureImportProgress(records, totalBytes))
    }
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    fun har(entry: JsonObject, files: Map<String, File>) {
        val request = requireNotNull(entry["request"] as? JsonObject) { "HAR request is missing." }
        val response = entry["response"] as? JsonObject
        val url = requireNotNull(request.text("url")) { "HAR request URL is missing." }
        val uri = java.net.URI(url); require(uri.scheme in setOf("http", "https", "ws", "wss") && uri.host != null) { "Unsupported HAR request URL." }
        fun body(container: JsonObject?, key: String, requestBody: Boolean): JsonObject {
            val text = files[key]
            if (text == null) {
                val captured = container?.get("_capture") as? JsonObject
                val knownEmpty = container?.get("size")?.jsonPrimitive?.longOrNull == 0L || (requestBody && container == null && request["bodySize"]?.jsonPrimitive?.longOrNull == 0L)
                return buildJsonObject { put("state", captured?.text("state") ?: if (knownEmpty) "EMPTY" else "UNAVAILABLE"); put("storedBytes", 0) }
            }
            val encoding = container?.text("encoding") ?: container?.text("_encoding")
            if (encoding != null && encoding != "base64") return buildJsonObject { put("state", "UNSUPPORTED"); put("storedBytes", 0) }
            val capture = container?.get("_capture") as? JsonObject
            return text.inputStream().buffered().use { raw ->
                val input = if (encoding == "base64") raw.decodingWith(Base64.Default) else raw
                payload(input, capture?.text("state") ?: "COMPLETE", capture == null, utf8Text = encoding == null)
            }
        }
        fun message(item: JsonObject, body: JsonObject, isRequest: Boolean): JsonObject = buildJsonObject {
            if (isRequest) { put("method", requireNotNull(item.text("method"))); put("url", url) }
            else { put("status", item["status"] ?: JsonNull); put("statusText", item["statusText"] ?: JsonNull) }
            put("protocol", item["httpVersion"] ?: JsonPrimitive("Unknown")); put("headers", item["headers"] ?: JsonArray(emptyList())); put("body", body)
        }
        record(buildJsonObject {
            put("kind", "http"); put("id", "import-$records")
            entry.text("startedDateTime")?.let { put("startedAt", Instant.parse(it).toEpochMilliseconds()) }
            put("request", message(request, body(request["postData"] as? JsonObject, "request.postData.text", true), true))
            response?.let { put("response", message(it, body(it["content"] as? JsonObject, "response.content.text", false), false)) }
            put("timings", buildJsonObject { entry["time"]?.let { put("total", it) } })
            put("originalHar", entry)
        })
    }
    override fun close() { try { index.close() } finally { metadata.close() }; progress(CaptureImportProgress(records, totalBytes)) }
}

internal fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
