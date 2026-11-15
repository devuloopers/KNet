package com.devuloopers.knet.application.usecase.traffic

import com.devuloopers.knet.application.contract.inspection.GraphQLInspectionMetadata
import com.devuloopers.knet.application.contract.traffic.*
import com.devuloopers.knet.domain.graphql.GraphQLHttpSource
import com.devuloopers.knet.traffic.inspection.InspectionAnnotationState
import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.traffic.model.body.*
import com.devuloopers.knet.traffic.model.http.*
import kotlinx.coroutines.*
import kotlin.time.Instant
import com.devuloopers.knet.application.usecase.traffic.TrafficCopyText as Text
import com.devuloopers.knet.application.usecase.traffic.TrafficCopyRecord as Record
import com.devuloopers.knet.application.usecase.traffic.TrafficCopySupport.safe
import com.devuloopers.knet.application.usecase.traffic.TrafficCopySupport.label
import com.devuloopers.knet.application.usecase.traffic.TrafficCopySupport.observed
import com.devuloopers.knet.application.usecase.traffic.TrafficCopySupport.size
import com.devuloopers.knet.application.usecase.traffic.TrafficCopySupport.host
import com.devuloopers.knet.application.usecase.traffic.TrafficCopySupport.parameters
import com.devuloopers.knet.application.usecase.traffic.TrafficCopySupport.pairs

/** Shared canonical renderer for stored Traffic and immutable API Studio HTTP execution snapshots. */
internal class TrafficCopyRenderer(
    private val bodies: BodyAccess,
    private val formatting: TrafficDetailsFormatting,
    private val messages: suspend (com.devuloopers.knet.traffic.id.ExchangeId) -> ProtocolMessagePage,
    private val annotations: suspend (com.devuloopers.knet.traffic.id.ExchangeId) -> List<com.devuloopers.knet.traffic.inspection.InspectionAnnotation>,
    private val presentations: ProtocolMessagePresentationRegistry,
) {
    suspend fun render(records: List<Record>, preset: TrafficCopyPreset, sourceName: String,
        studio: Boolean = false, deviceSource: Boolean = false, sourceForRecord: (suspend (Record) -> String)? = null): String {
        if (preset != TrafficCopyPreset.FULL) return TrafficCopyPresetRenderer(
            TrafficCopyContent(bodies, formatting), formatting, messages, annotations, presentations,
        ).render(records, preset, sourceName, studio = studio)
        val output = Text()
        output.line(if (studio) "EXCHANGE DETAILS" else "TRAFFIC DETAILS - ${records.size} ${if (records.size == 1) "ITEM" else "ITEMS"}")
        output.field(if (deviceSource) "Device" else "Source", sourceName)
        records.forEachIndexed { index, record ->
            currentCoroutineContext().ensureActive(); output.line()
            if (index > 0) { output.line("----------------------------------------"); output.line() }
            when {
                record.exchange != null -> http(output, index + 1, record.exchange)
                record.opaque != null -> opaque(output, index + 1, record.opaque)
                else -> { output.line("${index + 1}. Unavailable item (${record.key})"); output.line("[Cleared before copying]") }
            }
            sourceForRecord?.let { output.field("Device / source", it(record)) }
        }
        return output.toString()
    }

    private suspend fun http(out: Text, index: Int, exchange: HttpExchangeSnapshot) {
        val request = exchange.request
        val url = request.absoluteUrl()
        val path = request.pathWithoutQuery() ?: url
        out.line("$index. ${request.head.method.token} ${safe(path)}")
        out.field("Host", host(request))
        out.field("URL", url)
        out.field("Status", exchange.response?.head?.let { "${it.status.code}${it.reasonPhrase?.takeIf(String::isNotBlank)?.let { phrase -> " $phrase" }.orEmpty()}" } ?: label(exchange.state.name))
        exchange.timings.totalMillis?.let { out.field("Duration", "$it ms") }
        val requestBytes = observed(request.body); val responseBytes = observed(exchange.response?.body)
        val knownBytes = requestBytes.coerceAtMost(Long.MAX_VALUE - responseBytes) + responseBytes
        out.field("Size", size(knownBytes) + if (request.body is MessageBodyRef.Unavailable || exchange.response?.body is MessageBodyRef.Unavailable) " (known bytes; some body sizes unavailable)" else "")
        if (exchange.state != ExchangeState.COMPLETED) out.field("State", label(exchange.state.name))
        out.field("Time", Instant.fromEpochMilliseconds(exchange.startedAtEpochMillis).toString())
        out.field("Client protocol", request.head.protocol.token)
        exchange.response?.let { out.field("Upstream protocol", it.head.protocol.token) }
        exchange.terminalOutcome?.reason?.let { out.field("Termination", it.code.value) }
        val headers = request.head.headers.map { it.name.value to it.value }
        val body = body(request.body, headers, BODY_BYTES)
        val graphQL = formatting.graphQL(request.head.method.token, url, headers.firstOrNull { it.first.equals("content-type", true) }?.second.orEmpty(), body.decoded?.encodeToByteArray(), body.complete)
            ?.takeUnless { it.source == GraphQLHttpSource.JSON_BATCH }
        val params = parameters(url).filterNot { graphQL != null && it.first in GRAPHQL_PARAMETERS }
        val requestFields = buildList {
            if (params.isNotEmpty()) add("Params" to pairs(params))
            if (headers.isNotEmpty()) add("Headers" to pairs(headers))
            if (graphQL != null) {
                (graphQL.selectedOperation?.name ?: graphQL.suppliedOperationName)?.let { add("Operation name" to it) }
                graphQL.selectedOperation?.let { add("Operation type" to it.type.label) }
                if (graphQL.formattedQuery.isNotBlank()) add("Query" to graphQL.formattedQuery)
                add("Query source" to graphQL.queryProvenance.label)
                if (graphQL.variablesJson.isNotBlank()) add("Variables" to formatting.json(graphQL.variablesJson))
                if (graphQL.extensionsJson.isNotBlank()) add("Extensions" to formatting.json(graphQL.extensionsJson))
                graphQL.diagnostic?.let { add("GraphQL note" to it.description) }
                body.note?.let { add("Body availability" to it) }
            } else body.text?.let { add("Body" to it) }
            if (request.body == MessageBodyRef.Empty && exchange.state in setOf(ExchangeState.REQUEST_HEADERS, ExchangeState.REQUEST_STREAMING))
                add("Body availability" to "[Request body not finalized at copy time]")
            if (request.trailers.isNotEmpty()) add("Trailers" to pairs(request.trailers.map { it.name.value to it.value }))
        }
        out.section("Request", requestFields)
        val response = exchange.response
        if (response == null) out.section("Response", listOf("Availability" to "[No response captured; ${label(exchange.state.name)}]"))
        else {
            val responseHeaders = response.head.headers.map { it.name.value to it.value }
            val content = body(response.body, responseHeaders, BODY_BYTES)
            out.section("Response", buildList {
                if (responseHeaders.isNotEmpty()) add("Headers" to pairs(responseHeaders))
                content.text?.let { add("Body" to it) }
                if (response.body == MessageBodyRef.Empty && !exchange.state.isTerminal)
                    add("Body availability" to "[Response body not finalized at copy time]")
                if (response.trailers.isNotEmpty()) add("Trailers" to pairs(response.trailers.map { it.name.value to it.value }))
            })
        }
        annotations(exchange.id).filter { it.state == InspectionAnnotationState.COMPLETED }.mapNotNull { it.document }
            .filterNot { it.kind == GraphQLInspectionMetadata.DOCUMENT_KIND }.forEach { document ->
                out.section(document.title, document.fields.map { it.label to it.value })
            }
        val page = messages(exchange.id)
        if (page.totalCount > 0 || page.items.isNotEmpty()) {
            val sampled = page.items.take(MESSAGE_LIMIT)
            out.line(); out.line("Messages (${sampled.size} of ${maxOf(page.totalCount, page.items.size.toLong())})")
            sampled.sortedBy { it.sequence }.forEach { message ->
                currentCoroutineContext().ensureActive()
                if (message.exchangeId != exchange.id || (exchange.connectionId != null && message.connectionId != exchange.connectionId)) {
                    out.line("  [Unavailable: message parent does not match the copied exchange]")
                    return@forEach
                }
                out.line("  ${message.sequence}. ${safe(message.protocol.value)} / ${safe(message.kind.value)} / ${label(message.direction.name)}")
                val messageHeaders = listOf("Content-Type" to if (message.kind == com.devuloopers.knet.traffic.model.message.ProtocolMessageKind.BINARY || message.protocol == com.devuloopers.knet.traffic.model.message.MessageProtocolId.GRPC) "application/octet-stream" else "text/plain") +
                    message.compressionEncoding?.takeIf { message.compressed }?.let { listOf("Content-Encoding" to it) }.orEmpty()
                out.field("State", label(message.state.name), "    ")
                body(message.body, messageHeaders, MESSAGE_BYTES).text?.let { out.part("Payload", it, "    ") }
                message.terminationReason?.let { out.field("Termination", it.code.value, "    ") }
            }
            if (page.nextCursor != null || page.totalCount > sampled.size || page.items.size > sampled.size) out.line("  [Showing a bounded page; additional captured messages are not included]")
        }
    }

    private fun opaque(out: Text, index: Int, flow: OpaqueFlowSnapshot) {
        val destination = "${if (flow.destination.host.contains(':')) "[${flow.destination.host}]" else flow.destination.host}:${flow.destination.port}"
        out.line("$index. ${if (flow.security == OpaqueSecurityProtocol.UNKNOWN) flow.transport.name else flow.security.name} connection")
        out.field("Host", destination); out.field("Status", label(flow.state.name))
        flow.completedAtEpochMillis?.let { out.field("Duration", "${it - flow.startedAtEpochMillis} ms") }
        out.field("Uploaded", size(flow.uploadedBytes)); out.field("Downloaded", size(flow.downloadedBytes))
        out.field("Time", Instant.fromEpochMilliseconds(flow.startedAtEpochMillis).toString())
        out.section("Connection", buildList {
            add("Transport" to flow.transport.name); add("Security" to flow.security.name)
            flow.serverName?.let { add("Server name" to it) }
            flow.inspectionFailureReason?.let { add("Inspection failure" to label(it.name)) }
            flow.closure?.let { add("Closed by" to label(it.initiator.name)); add("Close reason" to label(it.reason.name)) }
            flow.terminalOutcome?.reason?.let { add("Termination" to it.code.value) }
            add("Payload" to "[HTTP headers and bodies are unavailable for this opaque connection]")
        })
    }

    private suspend fun body(ref: MessageBodyRef, headers: List<Pair<String, String>>, limit: Int): TrafficCopyBody =
        TrafficCopyContent(bodies, formatting).body(ref, headers, limit)

    private companion object {
        const val MAXIMUM_TEXT_CHARACTERS = TrafficCopyLimits.TEXT
        const val BODY_BYTES = TrafficCopyLimits.BODY
        const val MESSAGE_BYTES = TrafficCopyLimits.MESSAGE
        const val MESSAGE_LIMIT = TrafficCopyLimits.MESSAGES
        const val TOO_LARGE = TrafficCopyLimits.TOO_LARGE
        val GRAPHQL_PARAMETERS = setOf("query", "variables", "extensions", "operationName")
    }
}
