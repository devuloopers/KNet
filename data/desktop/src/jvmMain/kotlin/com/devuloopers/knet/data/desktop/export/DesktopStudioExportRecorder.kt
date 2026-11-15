package com.devuloopers.knet.data.desktop.export

import com.devuloopers.knet.application.contract.export.*
import com.devuloopers.knet.application.contract.copy.*
import com.devuloopers.knet.application.contract.traffic.TrafficCopyPreset
import com.devuloopers.knet.application.contract.apistudio.*
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutionResponseHead
import com.devuloopers.knet.domain.clientNetwork.model.ExecutionResult
import com.devuloopers.knet.domain.clientNetwork.model.OutboundRequestBody
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

/**
 * Per-execution disk-backed evidence, independent of Traffic recording and formatted UI windows.
 * Each session retains at most 64 MiB of bytes and 100,000 events; exceeding either bound is explicit.
 */
class DesktopStudioExportRecorder(root: File) : StudioExportRecorder, StudioCopySource, AutoCloseable {
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun close() { sessions.values.toList().forEach { it.release() } }
    /** Drains already scheduled cleanup during suspending shutdown/tests before their temporary root is removed. */
    suspend fun awaitCleanup() { cleanupScope.coroutineContext.job.children.toList().forEach { it.join() } }
    private val directory = root.apply { mkdirs(); setReadable(false, false); setReadable(true, true); setWritable(false, false); setWritable(true, true); setExecutable(false, false); setExecutable(true, true) }
    private val sessions = ConcurrentHashMap<String, Session>()
    init { directory.listFiles()?.filter { it.name.startsWith("studio-") }?.forEach { it.deleteRecursively() } }

    override suspend fun http(request: StudioExportHttpRequest): StudioExportSession {
        var owned: Session? = null
        try { return withContext(Dispatchers.IO) { create().also { owned = it; it.initializeHttp(request) } } }
        catch (failure: Throwable) { owned?.release(); throw failure }
    }
    override fun protocol(command: ApiStudioProtocolExecutionCommand, draft: ApiStudioProtocolDraft): StudioExportSession =
        create().apply { initializeProtocol(command, draft) }

    private fun create(): Session = synchronized(sessions) {
        check(sessions.size < 64) { "Too many retained API Studio exports; clear unused responses." }
        Session(Uuid.random().toString()).also { sessions[it.id] = it }
    }

    internal suspend fun snapshot(id: String, destination: File): Unit = withContext(Dispatchers.IO) {
        val session = requireNotNull(sessions[id]) { "This API Studio response is no longer available." }
        session.snapshot(destination, currentCoroutineContext())
    }

    /** Copies only bounded prefixes while the execution still owns its private files. */
    override suspend fun snapshot(id: String, preset: TrafficCopyPreset): StudioCopySnapshot = withContext(Dispatchers.IO) {
        val session = checkNotNull(sessions[id]) { "This API Studio response is no longer available." }
        session.copySnapshot(preset, currentCoroutineContext())
    }

    private inner class Session(override val id: String) : StudioExportSession {
        private val folder = File(directory, "studio-$id").apply { mkdirs() }
        private val guard = Any()
        private val availability = MutableStateFlow(false)
        override val eligible: StateFlow<Boolean> = availability
        private val copyAvailability = MutableStateFlow(false)
        override val responseReceived: StateFlow<Boolean> = copyAvailability
        private var parent: JsonObject = ExportJson.obj { }
        private var head: HttpExecutionResponseHead? = null
        private var stored = 0L
        private var observed = 0L
        private var terminal = false
        private var copyState = StudioCopyState.ACTIVE
        @Volatile private var released = false
        private var eventCount = 0L
        private var droppedEvents = 0L
        private var bodyChunks = false
        private var responseBytes = 0L
        private val copyFrames = mutableListOf<JsonObject>()
        private var messageCount = 0L
        private var receivedHeaders = emptyList<Pair<String, String>>()
        private var receivedTrailers = emptyList<Pair<String, String>>()
        private var nativeStatus: String? = null
        private var nativeFailure: String? = null
        private var knownEmptyRequest = false
        override var copyKind: com.devuloopers.knet.domain.request.descriptor.RequestKindId = com.devuloopers.knet.domain.request.descriptor.RequestKindId.HTTP
            private set
        private val started = System.currentTimeMillis()

        fun initializeHttp(request: StudioExportHttpRequest) = synchronized(guard) {
            knownEmptyRequest = request.body == OutboundRequestBody.None
            val semantic = when (val body = request.body) {
                OutboundRequestBody.None -> byteArrayOf()
                is OutboundRequestBody.Json -> body.content.encodeToByteArray()
                is OutboundRequestBody.Xml -> body.content.encodeToByteArray()
                is OutboundRequestBody.Text -> body.content.encodeToByteArray()
                is OutboundRequestBody.GraphQl -> body.content.encodeToByteArray()
                is OutboundRequestBody.FormUrlEncoded -> body.fields.joinToString("&") {
                    java.net.URLEncoder.encode(it.name, "UTF-8") + "=" + java.net.URLEncoder.encode(it.value, "UTF-8")
                }.encodeToByteArray()
                is OutboundRequestBody.Multipart -> byteArrayOf()
            }
            if (com.devuloopers.knet.engine.formatter.graphql.GraphQLHttpRequestFormatter.inspect(request.method.token, request.url,
                    request.headers.firstOrNull { it.first.equals("content-type", true) }?.second.orEmpty(), semantic.copyOf(minOf(semantic.size, 65_536)), semantic.size <= 65_536,
                    learnCaptured = false) != null) copyKind = com.devuloopers.knet.domain.request.descriptor.RequestKindId.GRAPHQL
            val reference = if (request.body is OutboundRequestBody.Multipart) ExportJson.obj {
                put("state", "UNAVAILABLE"); put("reason", "Transport-generated multipart boundary was not exposed by the executor.")
            } else retain("request.bin", semantic)
            parent = ExportJson.obj {
                put("kind", "http"); put("id", id); put("startedAt", started); put("state", "ACTIVE")
                put("source", ExportJson.obj { put("kind", "ApiStudio") })
                put("request", ExportJson.obj {
                    put("method", request.method.token); put("url", request.url); put("protocol", "")
                    put("headers", ExportJson.pairs(request.headers)); put("body", reference)
                    put("representation", "POST_SCRIPT_SEMANTIC_REQUEST")
                    put("note", "Transport-generated headers, redirects and encoding may differ; no raw-wire claim.")
                })
            }
        }

        fun initializeProtocol(command: ApiStudioProtocolExecutionCommand, draft: ApiStudioProtocolDraft) = synchronized(guard) {
            copyKind = command.document.kind
            parent = ExportJson.obj {
                put("kind", "protocol"); put("id", id); put("protocol", command.document.kind.value)
                put("startedAt", started); put("state", "ACTIVE"); put("source", ExportJson.obj { put("kind", "ApiStudio") })
                put("target", draft.targetUri ?: "${draft.targetHost}:${draft.targetPort}")
                put("host", draft.targetHost); put("port", draft.targetPort)
                put("operation", draft.operationId); put("headers", ExportJson.pairs(draft.metadata.filter { it.enabled }.map { it.name to it.value }))
                put("requestedProtocols", JsonArray(draft.requestedProtocols.map(::JsonPrimitive)))
            }
        }

        override suspend fun response(head: HttpExecutionResponseHead): Unit = withContext(Dispatchers.IO) { synchronized(guard) {
            if (released) return@synchronized
            this@Session.head = head.copy(headers = head.headers.toMap(), cookies = head.cookies.toMap(), orderedHeaders = head.orderedHeaders.toList())
            copyAvailability.value = true
            head.sentRequest?.let { sent ->
                val file = File(folder, "request.bin")
                if (file.exists()) stored -= file.length()
                val request = ExportJson.obj {
                    put("url", sent.url); put("method", sent.method); put("protocol", "")
                    put("headers", ExportJson.pairs(sent.headers.filterNot { it.first.equals(com.devuloopers.knet.traffic.model.TrafficAttributionHeader.NAME, true) }))
                    put("representation", "TRANSPORT_REQUEST")
                    put("body", sent.body?.let { retain("request.bin", it.copyBytes()) } ?: ExportJson.obj { put("state", if (knownEmptyRequest) "EMPTY" else "UNAVAILABLE") })
                }
                parent = JsonObject(parent + ("request" to request))
            }
            availability.value = head.headers.any { it.key.equals("content-type", true) && it.value.startsWith("text/event-stream", true) }
        } }
        override suspend fun append(bytes: ByteArray): Unit = withContext(Dispatchers.IO) { synchronized(guard) {
            if (released) return@synchronized
            bodyChunks = true; observed += bytes.size; responseBytes += bytes.size
            val count = minOf(bytes.size.toLong(), MAXIMUM_BYTES - stored).coerceAtLeast(0).toInt()
            if (count > 0) { java.io.FileOutputStream(File(folder, "response.bin"), true).use { it.write(bytes, 0, count) }; stored += count }
        } }
        override suspend fun complete(result: ExecutionResult): Unit = withContext(Dispatchers.IO) {
            if (head == null && result.statusCode in 100..599 && result.failureReason == null) response(HttpExecutionResponseHead(
                result.statusCode, result.statusText, result.headers, result.cookies, result.protocol, result.orderedHeaders, result.sentRequest))
            synchronized(guard) {
                if (released) return@synchronized
                // Terminal-only executors may provide original bytes. Never substitute a decoded/pretty String.
                if (!bodyChunks && result.rawResponse != null) {
                    val raw = requireNotNull(result.rawResponse).copyBytes()
                    retain("response.bin", raw)
                    responseBytes = raw.size.toLong()
                } else if (!bodyChunks && result.responseSizeBytes > 0) {
                    parent = JsonObject(parent + ("responsePayloadUnavailable" to JsonPrimitive(true)))
                    responseBytes = result.responseSizeBytes
                }
                availability.value = head != null
                terminal = true
                copyState = when (result.failureReason) {
                    null -> StudioCopyState.COMPLETED
                    com.devuloopers.knet.domain.clientNetwork.model.NetworkFailureReason.Cancelled -> StudioCopyState.PARTIAL
                    else -> StudioCopyState.FAILED
                }
                parent = JsonObject(parent + mapOf("state" to JsonPrimitive(if (result.failureReason == null) "COMPLETED" else "PARTIAL"),
                    "timings" to ExportJson.obj { result.timings.totalMillis?.let { put("total", it) } }))
                result.failureReason?.let { parent = JsonObject(parent + ("copyFailureReason" to JsonPrimitive(it.toString()))) }
            }
        }

        override suspend fun event(event: ApiStudioProtocolExecutionEvent): Unit = withContext(Dispatchers.IO) { synchronized(guard) {
            if (released) return@synchronized
            if (event is ApiStudioProtocolExecutionEvent.Started && event.serverResponseReceived) availability.value = true
            if (event is ApiStudioProtocolExecutionEvent.Message && event.message.direction == ApiStudioProtocolMessageDirection.INBOUND) availability.value = true
            if (event is ApiStudioProtocolExecutionEvent.Completed) { availability.value = true; terminal = true; copyState = StudioCopyState.COMPLETED; parent = JsonObject(parent + ("state" to JsonPrimitive("COMPLETED"))) }
            if (event is ApiStudioProtocolExecutionEvent.Failed) { if (event.serverResponseReceived) availability.value = true; terminal = true; copyState = StudioCopyState.FAILED; parent = JsonObject(parent + ("state" to JsonPrimitive("FAILED"))) }
            when (event) {
                is ApiStudioProtocolExecutionEvent.Started -> receivedHeaders = event.responseHeaders.toList()
                is ApiStudioProtocolExecutionEvent.Completed -> { receivedTrailers = event.trailers.toList(); nativeStatus = event.statusCode }
                is ApiStudioProtocolExecutionEvent.Failed -> { receivedTrailers = event.trailers.toList(); if (event.responseHeaders.isNotEmpty()) receivedHeaders = event.responseHeaders.toList(); nativeStatus = event.responseStatus?.toString(); nativeFailure = event.code; parent = JsonObject(parent + ("state" to JsonPrimitive("FAILED"))) }
                is ApiStudioProtocolExecutionEvent.Message -> messageCount++
            }
            copyAvailability.value = availability.value
            if (++eventCount > MAXIMUM_EVENTS) { droppedEvents++; return@synchronized }
            val value = when (event) {
                is ApiStudioProtocolExecutionEvent.Message -> ExportJson.obj {
                    put("kind", "message"); put("exchangeId", id); put("sequence", event.message.sequence)
                    put("direction", event.message.direction.name); put("occurredAt", System.currentTimeMillis())
                    put("contentType", event.message.contentType); put("body", retain("message-$eventCount.bin", event.message.copyPayload()))
                }
                is ApiStudioProtocolExecutionEvent.Started -> ExportJson.obj { put("kind", "event"); put("exchangeId", id); put("event", "STARTED"); put("serverResponseReceived", event.serverResponseReceived); put("headers", ExportJson.pairs(event.responseHeaders)) }
                is ApiStudioProtocolExecutionEvent.Completed -> ExportJson.obj {
                    put("kind", "event"); put("exchangeId", id); put("event", "COMPLETED"); put("status", event.statusCode)
                    put("trailers", ExportJson.pairs(event.trailers))
                }
                is ApiStudioProtocolExecutionEvent.Failed -> ExportJson.obj {
                    put("kind", "event"); put("exchangeId", id); put("event", "FAILED"); put("code", event.code)
                    put("trailers", ExportJson.pairs(event.trailers))
                    event.responseStatus?.let { put("responseStatus", it) }; put("headers", ExportJson.pairs(event.responseHeaders))
                }
            }
            if (event is ApiStudioProtocolExecutionEvent.Message && copyFrames.size < 32) {
                // gRPC events carry original protobuf bytes and a separate engine-decoded JSON view.
                // Retain that bounded interpretation only for copy; the archive still contains original bytes.
                val interpreted = event.message.displayText.takeIf {
                    copyKind == com.devuloopers.knet.domain.request.descriptor.RequestKindId.GRPC &&
                        event.message.contentType.equals("application/json", true) && it.length <= 16_384
                }
                copyFrames += if (interpreted == null) value else JsonObject(value + ("copyJson" to JsonPrimitive(interpreted)))
            }
            File(folder, "events.jsonl").appendText(value.toString() + "\n")
        } }

        override fun interrupted() = synchronized(guard) { if (!terminal) { terminal = true; copyState = StudioCopyState.PARTIAL; if (head != null) availability.value = true; parent = JsonObject(parent + ("state" to JsonPrimitive("PARTIAL"))) } }
        override fun release() {
            if (sessions.remove(id) != null) {
                released = true; availability.value = false; copyAvailability.value = false
                cleanupScope.launch { synchronized(guard) { folder.deleteRecursively() } }
            }
        }


        private fun retain(name: String, bytes: ByteArray): JsonObject {
            val count = minOf(bytes.size.toLong(), MAXIMUM_BYTES - stored).coerceAtLeast(0).toInt()
            val file = File(folder, name); file.writeBytes(bytes.copyOf(count)); stored += count
            return reference(file, bytes.size.toLong(), count.toLong())
        }
        private fun reference(file: File, total: Long, length: Long = file.length(), checkCancellation: () -> Unit = {}): JsonObject = ExportJson.obj {
            put("state", if (length == total) "COMPLETE" else "TRUNCATED")
            put("entry", "payloads/${file.name}"); put("observedBytes", total); put("storedBytes", length)
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input -> val buffer = ByteArray(65_536); while (true) { checkCancellation(); val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) } }
            put("sha256", digest.digest().joinToString("") { "%02x".format(it) })
        }
        fun copySnapshot(preset: TrafficCopyPreset, context: kotlin.coroutines.CoroutineContext): StudioCopySnapshot = synchronized(guard) {
            context.ensureActive()
            check(!released && (head != null || availability.value)) { "Send the request and receive a response before copying." }
            StudioCopySnapshotMapper(folder, context).snapshot(id, parent, head, responseBytes, terminal, copyState,
                copyFrames.toList(), messageCount, receivedHeaders, receivedTrailers, nativeStatus, nativeFailure, preset)
        }

        fun snapshot(destination: File, context: kotlin.coroutines.CoroutineContext) = synchronized(guard) {
            check(!released && availability.value) { "Send the request and receive a response before exporting." }
            val response = head?.let { received -> ExportJson.obj {
                put("status", received.statusCode); put("statusText", received.statusText); put("protocol", received.protocol?.token.orEmpty())
                put("headers", ExportJson.pairs(received.orderedHeaders))
                val file = File(folder, "response.bin")
                val captured = if (file.exists()) reference(file, responseBytes, checkCancellation = context::ensureActive) else ExportJson.obj {
                    put("state", if (responseBytes == 0L) "EMPTY" else "UNAVAILABLE"); put("observedBytes", responseBytes)
                }
                put("body", if (captured["state"]?.jsonPrimitive?.content == "COMPLETE" && (!terminal || parent["state"]?.jsonPrimitive?.content == "PARTIAL"))
                    JsonObject(captured + ("state" to JsonPrimitive(if (terminal) "PARTIAL" else "ACTIVE_PREFIX"))) else captured)
            } }
            val metadata = parent.toMutableMap()
            if (!terminal) metadata["state"] = JsonPrimitive("ACTIVE_AT_SNAPSHOT")
            if (response != null) metadata["response"] = response
            metadata["droppedEvents"] = JsonPrimitive(droppedEvents)
            File(destination, "records.jsonl").bufferedWriter().use { output ->
                output.appendLine(JsonObject(metadata).toString())
                File(folder, "events.jsonl").takeIf { it.exists() }?.forEachLine { context.ensureActive(); output.appendLine(it) }
            }
            val payloads = File(destination, "payloads").apply { mkdirs() }
            Files.newDirectoryStream(folder.toPath(), "*.bin").use { paths -> paths.forEach { path ->
                context.ensureActive()
                path.toFile().inputStream().use { input -> File(payloads, path.fileName.toString()).outputStream().use { output ->
                    val buffer = ByteArray(65_536)
                    while (true) { context.ensureActive(); val count = input.read(buffer); if (count < 0) break; output.write(buffer, 0, count) }
                } }
            } }
        }
    }
    private companion object { const val MAXIMUM_BYTES = 64L * 1024 * 1024; const val MAXIMUM_EVENTS = 100_000 }
}
