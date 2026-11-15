package com.devuloopers.knet.application.usecase.traffic

import com.devuloopers.knet.application.contract.copy.*
import com.devuloopers.knet.application.contract.traffic.*
import com.devuloopers.knet.application.contract.traffic.TrafficCopyPreset.*
import com.devuloopers.knet.application.contract.apistudio.*
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutionResponseHead
import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.traffic.model.body.MessageBodyRef
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

/** Clipboard command for an execution ID; editor drafts and Traffic admission are never inputs. */
public fun interface PrepareStudioCopyUseCase {
    /** Returns one bounded frozen representation or throws without modifying the clipboard. */
    public suspend fun execute(id: String, preset: TrafficCopyPreset): String
}

/** Uses immutable execution evidence with the same canonical renderers and formatters as Traffic. */
public class CopyStudioExecutionUseCase(
    private val source: StudioCopySource,
    private val formatting: TrafficDetailsFormatting,
    private val presentations: ProtocolMessagePresentationRegistry,
    private val streams: HttpResponseStreamInterpreterRegistry,
) : PrepareStudioCopyUseCase {
    /** Resolves only [id], validates its owner and prepares [preset] on IO within shared clipboard limits. */
    override suspend fun execute(id: String, preset: TrafficCopyPreset): String = withContext(Dispatchers.IO) {
        val snapshot = source.snapshot(id, preset)
        check(snapshot.id == id && (snapshot.http?.id?.value ?: snapshot.protocol?.id) == id) { "The execution copy owner changed. Copy again." }
        snapshot.http?.let { exchange ->
            if (preset == SSE_EVENTS || preset == SSE_DATA) return@withContext stream(snapshot, exchange, preset)
            val record = TrafficCopyRecord(TrafficRecordId.Exchange(exchange.id), exchange, null, null)
            require(record.weight() <= TrafficCopyLimits.TEXT) { TrafficCopyLimits.TOO_LARGE }
            val label = "API Studio" + snapshot.note?.let { "; $it" }.orEmpty()
            // Semantic fallback bytes have not passed through content-encoding. An encoded replay
            // would mislabel those bytes, so require transport evidence rather than a misleading command.
            if (preset == CURL && !snapshot.requestTransportCaptured && exchange.request.head.headers.any {
                    it.name.value.equals("content-encoding", true) && !it.value.equals("identity", true)
                }) return@withContext "[cURL unavailable: encoded transport request bytes were not exposed; use Export]"
            var text = TrafficCopyRenderer(snapshot, formatting, { snapshot.messages }, { emptyList() }, presentations)
                .render(listOf(record), preset, label, studio = true)
            if (preset == CURL && snapshot.note != null) text = "# ${TrafficCopySupport.safe(snapshot.note)}\n$text"
            require(text.length <= TrafficCopyLimits.TEXT) { TrafficCopyLimits.TOO_LARGE }
            return@withContext text
        }
        StudioProtocolCopyRenderer(snapshot, formatting).render(preset)
    }

    private suspend fun stream(snapshot: StudioCopySnapshot, exchange: HttpExchangeSnapshot, preset: TrafficCopyPreset): String {
        val response = checkNotNull(exchange.response) { "No response has been received." }
        val headers = response.head.headers.map { it.name.value to it.value }.toMap()
        val raw = TrafficCopyContent(snapshot, formatting).raw(response.body)
        val interpreter = streams.open(HttpExecutionResponseHead(response.head.status.code, response.head.reasonPhrase.orEmpty(), headers,
            emptyMap(), response.head.protocol, response.head.headers.map { it.name.value to it.value })) ?: return "[No supported event stream in this response]"
        val output = TrafficCopyText()
        var finished = false
        try {
            val updates = interpreter.accept(raw.bytes ?: byteArrayOf()) + if (raw.complete && exchange.state.isTerminal) {
                finished = true
                interpreter.finish()
            } else emptyList()
            val records = updates.filterIsInstance<HttpLiveResponseUpdate.Record>()
            records.take(32).forEachIndexed { index, event ->
                currentCoroutineContext().ensureActive()
                if (index > 0) output.line()
                output.field("Record", event.value.sequence.toString())
                output.part(if (preset == SSE_DATA) "Data" else "Event", if (preset == SSE_DATA) formatting.json(event.value.data) else event.value.raw)
            }
            if (records.isEmpty()) output.line("[No complete stream records in the copied prefix]")
            raw.note?.let { output.line(it) }
            if (!raw.complete || !exchange.state.isTerminal) output.line("[Snapshot prefix: the response is incomplete or still active]")
            if (records.size > 32) output.line("[Showing the first 32 records in the copied prefix]")
            updates.filterIsInstance<HttpLiveResponseUpdate.Gap>().forEach { output.line("[Stream gap: ${TrafficCopySupport.safe(it.reason)}]") }
            return output.toString()
        } finally { if (!finished) runCatching { interpreter.finish() } }
    }
}

/** Native session copy retains message direction/metadata and never creates a fictitious HTTP exchange. */
internal class StudioProtocolCopyRenderer(private val snapshot: StudioCopySnapshot, private val formatting: TrafficDetailsFormatting) {
    private val parent = checkNotNull(snapshot.protocol)
    suspend fun render(preset: TrafficCopyPreset): String {
        val parts = when (preset) {
            FULL, STRUCTURED_JSON -> metadata() + frames(preset)
            SUMMARY, CONNECTION, WEBSOCKET_HANDSHAKE, GRPC_METADATA -> metadata()
            URL -> listOf("Target" to parent.target)
            DESTINATION -> listOf("Host and port" to destination())
            GRPC_METHOD -> listOf("Service and method" to (parent.operation ?: "[Operation unavailable]"))
            FAILURE_CLOSURE -> listOf("State" to parent.state.name, "Failure" to (parent.failure ?: "[No recorded failure]"), "Native status" to (parent.status?.toString() ?: "[Unavailable]"))
            WEBSOCKET_MESSAGES, WEBSOCKET_CLIENT_MESSAGES, WEBSOCKET_SERVER_MESSAGES, GRPC_MESSAGES -> frames(preset)
            REQUEST_HEADERS -> listOf("Request metadata" to headers(parent.requestHeaders))
            RESPONSE_HEADERS -> listOf("Response metadata" to headers(parent.responseHeaders))
            else -> listOf("Availability" to "[This preset does not apply to a native ${parent.kind.value} session]")
        }
        val output = TrafficCopyText()
        if (preset == STRUCTURED_JSON) {
            val json = buildJsonObject {
                put("format", "knet-studio-details"); put("version", 1); put("id", parent.id); put("protocol", parent.kind.value)
                put("fields", buildJsonArray { parts.forEach { (name, value) -> add(buildJsonObject { put("name", name); put("text", TrafficCopyText.bounded(value)) }) } })
            }
            output.append(Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), json))
        } else if (preset == URL || preset == DESTINATION) output.append(if (preset == DESTINATION) destination() else parent.target)
        else {
            output.line("${parent.kind.value} session"); output.field("Target", parent.target)
            parts.forEach { (name, value) -> output.part(name, value) }
        }
        return if (preset == URL || preset == DESTINATION) output.literal() else output.toString()
    }
    private fun destination(): String = (if (':' in parent.destination.host) "[${parent.destination.host}]" else parent.destination.host) +
        parent.destination.port?.let { ":$it" }.orEmpty()
    private fun metadata(): List<Pair<String, String>> = listOf("Protocol" to parent.kind.value, "State" to parent.state.name,
        "Operation" to (parent.operation ?: "[Unavailable]"), "Request metadata" to headers(parent.requestHeaders),
        "Response metadata" to headers(parent.responseHeaders), "Trailers" to headers(parent.trailers),
        "Native status" to (parent.status ?: "[Unavailable]"), "Failure" to (parent.failure ?: "[No recorded failure]"))
    private fun headers(values: List<com.devuloopers.knet.traffic.model.http.HeaderField>): String = TrafficCopySupport.pairs(values.map { it.name.value to it.value }).ifEmpty { "[No captured metadata]" }
    private suspend fun frames(preset: TrafficCopyPreset): List<Pair<String, String>> = buildList {
        val content = TrafficCopyContent(snapshot, formatting)
        for (message in snapshot.messages.items) {
            currentCoroutineContext().ensureActive()
            if (message.exchangeId.value != parent.id) { add("Availability" to "[Message owner mismatch]"); continue }
            if (preset == WEBSOCKET_CLIENT_MESSAGES && message.direction != TrafficDirection.CLIENT_TO_SERVER || preset == WEBSOCKET_SERVER_MESSAGES && message.direction != TrafficDirection.SERVER_TO_CLIENT) continue
            val headers = listOf("Content-Type" to if (message.kind == com.devuloopers.knet.traffic.model.message.ProtocolMessageKind.BINARY) "application/octet-stream" else "text/plain")
            val interpretation = snapshot.messagePresentations[message.id]
            val text = if (interpretation != null) "[Engine-decoded gRPC JSON]\n" + formatting.json(interpretation.text) +
                content.raw(message.body, TrafficCopyLimits.MESSAGE).note?.let { "\nOriginal protobuf availability: $it" }.orEmpty()
                else content.body(message.body, headers, TrafficCopyLimits.MESSAGE).text ?: "[Empty payload]"
            add("Message ${message.sequence} (${message.direction.name})" to text)
        }
        if (isEmpty()) add("Messages" to "[No matching captured messages]")
        if (snapshot.messages.totalCount > snapshot.messages.items.size) add("Availability" to "[Only the first ${snapshot.messages.items.size} of ${snapshot.messages.totalCount} messages were considered]")
    }
}
