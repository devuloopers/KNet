package com.devuloopers.knet.data.desktop.export

import com.devuloopers.knet.application.contract.copy.*
import com.devuloopers.knet.application.contract.traffic.*
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutionResponseHead
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.traffic.id.*
import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.traffic.model.body.*
import com.devuloopers.knet.traffic.model.http.*
import com.devuloopers.knet.traffic.model.message.*
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*
import java.io.File
import java.net.URI
import kotlin.coroutines.CoroutineContext

/** Maps private execution evidence to canonical clipboard records; each file read is a bounded prefix. */
internal class StudioCopySnapshotMapper(private val folder: File, private val context: CoroutineContext) {
    private val chunks = mutableMapOf<BodyId, BodyChunk>()
    fun snapshot(id: String, parent: JsonObject, head: HttpExecutionResponseHead?, responseBytes: Long, terminal: Boolean, state: StudioCopyState,
        frames: List<JsonObject>, messageCount: Long, responseHeaders: List<Pair<String, String>>, trailers: List<Pair<String, String>>,
        status: String?, failure: String?, preset: TrafficCopyPreset): StudioCopySnapshot {
        require(parent.toString().length <= 4_194_304) { "The execution metadata exceeds the clipboard limit. Use Export." }
        val needs = StudioCopyPayloadNeeds.forPreset(preset)
        val active = !terminal
        val exchangeId = ExchangeId(id); val connection = ConnectionId("studio-$id")
        val http = if (parent.text("kind") == "http") {
            val request = parent["request"]!!.jsonObject
            val uri = URI(request.text("url")); val scheme = HttpScheme.fromToken(uri.scheme ?: "http")
            require(uri.host != null) { "This execution has no valid absolute request URL." }
            val target = RequestTarget.Absolute(scheme, Authority(uri.host.removeSurrounding("[", "]"), uri.port.takeIf { it > 0 }),
                uri.rawPath.ifEmpty { "/" } + uri.rawQuery?.let { "?$it" }.orEmpty())
            val req = HttpRequestSnapshot(RequestHead(HttpMethod.fromToken(request.text("method")), target,
                ApplicationProtocol.fromToken(request.text("protocol").ifEmpty { "Unavailable" }), headers(request["headers"])),
                body(request["body"]?.jsonObject, needs.request, 65_536))
            val response = head?.let { received ->
                val file = File(folder, "response.bin")
                val ref = buildJsonObject {
                    put("state", when { !file.exists() && responseBytes == 0L -> "EMPTY"; !file.exists() -> "UNAVAILABLE";
                        active || state != StudioCopyState.COMPLETED -> "PARTIAL"; file.length() < responseBytes -> "TRUNCATED"; else -> "COMPLETE" })
                    put("entry", "payloads/response.bin"); put("storedBytes", file.takeIf(File::exists)?.length() ?: 0L); put("observedBytes", responseBytes)
                }
                HttpResponseSnapshot(ResponseHead(received.protocol ?: ApplicationProtocol.fromToken("Unavailable"), HttpStatus(received.statusCode), received.statusText,
                    received.orderedHeaders.map { HeaderField(HeaderName(it.first), it.second) }), body(ref, needs.response, 65_536))
            }
            HttpExchangeSnapshot(exchangeId, connection, request = req, response = response, origin = TrafficOrigin.ApiStudio,
                state = if (active) ExchangeState.RESPONSE_STREAMING else if (state == StudioCopyState.COMPLETED) ExchangeState.COMPLETED else if (state == StudioCopyState.FAILED) ExchangeState.FAILED else ExchangeState.CANCELLED,
                terminalOutcome = if (active) null else if (state == StudioCopyState.COMPLETED) ExchangeTerminalOutcome.Completed else if (state == StudioCopyState.FAILED)
                    ExchangeTerminalOutcome.Failed(TrafficTerminationReason.Unspecified.FAILURE) else ExchangeTerminalOutcome.Cancelled(TrafficTerminationReason.Unspecified.CANCELLATION),
                timings = ExchangeTimings(totalMillis = parent["timings"]?.jsonObject?.get("total")?.jsonPrimitive?.longOrNull), startedAtEpochMillis = parent["startedAt"]!!.jsonPrimitive.long)
        } else null
        val native = if (http == null) StudioProtocolCopyRecord(id, RequestKindId(parent.text("protocol")), parent.text("target"), Authority(parent.text("host").removeSurrounding("[", "]"), parent["port"]!!.jsonPrimitive.int), parent["operation"]?.jsonPrimitive?.contentOrNull,
            headers(parent["headers"]), responseHeaders.map { HeaderField(HeaderName(it.first), it.second) }, trailers.map { HeaderField(HeaderName(it.first), it.second) }, state, status, failure) else null
        val protocol = when (native?.kind) { RequestKindId.GRPC -> MessageProtocolId.GRPC; else -> if (http != null) MessageProtocolId.SSE else MessageProtocolId.WEBSOCKET }
        val captured = if (!needs.messages) emptyList() else frames.take(32).mapIndexed { index, frame ->
            context.ensureActive()
            ProtocolMessageSnapshot(ProtocolMessageId("studio-$id-$index"), connection, exchangeId, null, protocol,
                if (native?.kind != RequestKindId.GRPC && (frame.text("contentType").startsWith("text/", true) || frame.text("contentType").contains("json", true))) ProtocolMessageKind.TEXT else ProtocolMessageKind.BINARY,
                if (frame.text("direction") == "OUTBOUND") TrafficDirection.CLIENT_TO_SERVER else TrafficDirection.SERVER_TO_CLIENT,
                frame["sequence"]!!.jsonPrimitive.long, frame["occurredAt"]!!.jsonPrimitive.long, null,
                frame["body"]!!.jsonObject["observedBytes"]?.jsonPrimitive?.longOrNull ?: 0L, false, null,
                body(frame["body"]!!.jsonObject, true, 16_384), ProtocolMessageState.COMPLETE)
        }
        return StudioCopySnapshot(id, http, native, ProtocolMessagePage(captured, null, messageCount), chunks,
            listOfNotNull(if (http != null && parent["request"]?.jsonObject?.text("representation") != "TRANSPORT_REQUEST")
                "Submitted post-script request; transport-generated headers, encoding and redirects were not exposed." else null,
                parent.text("copyFailureReason").takeIf(String::isNotEmpty)?.let { "Execution diagnostic: $it" }).takeIf { it.isNotEmpty() }?.joinToString(" "),
            messagePresentations = captured.mapNotNull { message ->
                val frame = frames.firstOrNull { it["sequence"]?.jsonPrimitive?.longOrNull == message.sequence }
                val json = frame?.text("copyJson")?.takeIf { it.isNotEmpty() && it.length <= 16_384 }
                if (native?.kind == RequestKindId.GRPC && json != null && runCatching { Json.parseToJsonElement(json) }.getOrNull().let { it is JsonObject || it is JsonArray })
                    message.id to com.devuloopers.knet.application.contract.traffic.ProtocolMessagePresentation("Decoded gRPC message", "application/json", json)
                else null
            }.toMap(), requestTransportCaptured = parent["request"]?.jsonObject?.text("representation") == "TRANSPORT_REQUEST")
    }

    private fun body(ref: JsonObject?, read: Boolean, limit: Int): MessageBodyRef {
        if (ref == null || ref.text("state") == "UNAVAILABLE") return MessageBodyRef.Unavailable(BodyCaptureOutcome.Skipped(BodySkipReason.METADATA_ONLY_POLICY))
        if (ref.text("state") == "EMPTY") return MessageBodyRef.Empty
        val observed = ref["observedBytes"]?.jsonPrimitive?.longOrNull ?: 0L
        val stored = ref["storedBytes"]?.jsonPrimitive?.longOrNull ?: 0L
        val id = BodyId("studio-copy-${chunks.size}-${ref.text("entry").substringAfterLast('/')}")
        if (read) {
            context.ensureActive()
            val name = ref.text("entry").removePrefix("payloads/")
            require(name.isNotEmpty() && '/' !in name && '\\' !in name && name != "." && name != "..") { "Invalid private execution body reference." }
            val file = File(folder, name)
            if (!file.isFile) return MessageBodyRef.Unavailable(BodyCaptureOutcome.Failed(BodyFailure.SourceFailed))
            val bytes = file.inputStream().use { it.readNBytes(minOf(stored, limit.toLong()).toInt()) }
            chunks[id] = BodyChunk(bytes, 0, bytes.size.toLong() == stored)
        }
        return MessageBodyRef.Available(BodyRef(id, maxOf(observed, stored), stored, outcome =
            if (ref.text("state") == "COMPLETE" && observed == stored) BodyCaptureOutcome.Complete else BodyCaptureOutcome.Truncated(stored)))
    }
    private fun headers(element: JsonElement?): List<HeaderField> = element?.jsonArray.orEmpty().map { it.jsonObject }.filter { it.text("name").isNotBlank() }
        .map { HeaderField(HeaderName(it.text("name")), it.text("value")) }
    private fun JsonObject.text(name: String): String = this[name]?.jsonPrimitive?.contentOrNull.orEmpty()
}
