package com.devuloopers.knet.application.usecase.traffic

import com.devuloopers.knet.application.contract.traffic.*
import com.devuloopers.knet.application.contract.traffic.TrafficCopyPreset.*
import com.devuloopers.knet.application.contract.inspection.InspectionAnnotationStore
import com.devuloopers.knet.domain.graphql.GraphQLHttpSource
import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.traffic.model.body.MessageBodyRef
import com.devuloopers.knet.traffic.model.http.*
import com.devuloopers.knet.traffic.model.message.*
import com.devuloopers.knet.application.usecase.traffic.TrafficCopySupport.safe
import com.devuloopers.knet.application.usecase.traffic.TrafficCopySupport.label
import com.devuloopers.knet.application.usecase.traffic.TrafficCopySupport.host
import com.devuloopers.knet.application.usecase.traffic.TrafficCopySupport.pairs
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*
import kotlin.time.Instant

/** One structured evidence block; clipboard renderers never parse the human-readable full-details output. */
internal data class TrafficCopyPart(val name: String, val value: String)

/** Resolves only the selected preset's data. Address, headers, summary and timing actions never read bodies. */
internal class TrafficCopyPresetRenderer(
    private val content: TrafficCopyContent,
    private val formatting: TrafficDetailsFormatting,
    private val messages: suspend (com.devuloopers.knet.traffic.id.ExchangeId) -> ProtocolMessagePage,
    private val annotations: suspend (com.devuloopers.knet.traffic.id.ExchangeId) -> List<com.devuloopers.knet.traffic.inspection.InspectionAnnotation>,
    private val presentations: ProtocolMessagePresentationRegistry,
) {
    private val json = Json { prettyPrint = true; prettyPrintIndent = "  " }

    suspend fun render(records: List<TrafficCopyRecord>, preset: TrafficCopyPreset, source: String, studio: Boolean = false): String {
        val out = TrafficCopyText()
        if (preset == STRUCTURED_JSON) {
            out.append("{\n  \"format\": \"knet-traffic-details\",\n  \"version\": 1,\n  \"source\": ${JsonPrimitive(source)},\n  \"items\": [\n")
            records.forEachIndexed { index, record ->
                currentCoroutineContext().ensureActive()
                if (index > 0) out.append(",\n")
                val document = buildJsonObject {
                    put("id", record.key); put("kind", if (record.exchange != null) "http" else if (record.opaque != null) "opaque" else "unavailable")
                    put("title", title(record))
                    put("source", if (studio) "apiStudio" else when (val ingress = TrafficSource.fromIngress(record.ingress?.ingress)) {
                        is TrafficSource.Device -> ingress.id.value
                        TrafficSource.Other -> "other"
                        TrafficSource.All -> "all"
                    })
                    put("fields", buildJsonArray { for (part in parts(record, STRUCTURED_JSON)) add(buildJsonObject {
                        put("name", part.name); put("text", TrafficCopyText.bounded(part.value))
                    }) })
                }
                // Serialize each record independently so a large selected group never builds an unbounded JSON tree.
                json.encodeToString(JsonElement.serializer(), document).lineSequence().forEachIndexed { line, value ->
                    if (line > 0) out.append("\n"); out.append("    $value")
                }
            }
            out.append("\n  ]\n}\n")
            return out.toString()
        }
        records.forEachIndexed { index, record ->
            currentCoroutineContext().ensureActive()
            val parts = parts(record, preset)
            when {
                preset in ADDRESS_LINES -> out.line(safe(parts.single().value.let { if (parts.single().name == "Availability") "$it (${record.key})" else it }))
                preset == CURL -> {
                    if (records.size > 1) out.line("# ${index + 1}. ${safe(title(record))}")
                    val value = parts.single().value
                    out.line(if (value.startsWith("[")) "# $value" else value)
                    if (index < records.lastIndex) out.line()
                }
                records.size == 1 && preset in COMPONENTS && parts.size == 1 -> {
                    // Component copies must preserve meaningful trailing whitespace and CRLF for direct paste.
                    out.append(parts.single().value)
                    return out.literal()
                }
                else -> {
                    if (index > 0) { out.line(); out.line("----------------------------------------"); out.line() }
                    out.line("${index + 1}. ${safe(title(record))}")
                    record.exchange?.let { out.field("URL", it.request.absoluteUrl()) }
                    record.opaque?.let { out.field("Host", TrafficCopySupport.destination(it.destination.host, it.destination.port)) }
                    parts.forEach { out.part(it.name, it.value) }
                }
            }
        }
        return if (preset in ADDRESS_LINES || preset == CURL) out.literal().removeSuffix("\n") else out.toString()
    }

    private suspend fun parts(record: TrafficCopyRecord, preset: TrafficCopyPreset): List<TrafficCopyPart> {
        record.exchange?.let { return http(it, preset) }
        record.opaque?.let { return opaque(it, preset) }
        return listOf(TrafficCopyPart("Availability", "[Cleared before copying: ${safe(record.key)}]"))
    }

    private suspend fun http(exchange: HttpExchangeSnapshot, preset: TrafficCopyPreset): List<TrafficCopyPart> {
        val request = exchange.request
        val url = request.absoluteUrl()
        val response = exchange.response
        return when (preset) {
            URL -> one("URL", url)
            DESTINATION -> one("Host and port", host(request))
            PATH_QUERY -> one("Path and query", when (val target = request.head.target) {
                is RequestTarget.Absolute -> target.pathAndQuery
                is RequestTarget.Origin -> target.pathAndQuery
                else -> "[No recorded path and query for this request target]"
            })
            QUERY_PARAMETERS -> one("Query parameters", TrafficCopySupport.pairs(TrafficCopySupport.parameters(url)).ifEmpty { "[No query parameters]" })
            SUMMARY -> summary(exchange)
            REQUEST_HEADERS -> one("Request headers", headerText(request.head.headers))
            REQUEST_COOKIES -> one("Request cookies", cookieText(request.head.headers, "cookie"))
            RESPONSE_HEADERS -> one("Response headers", response?.let { headerText(it.head.headers) } ?: NO_RESPONSE)
            RESPONSE_COOKIES -> one("Response cookies", response?.let { cookieText(it.head.headers, "set-cookie") } ?: NO_RESPONSE)
            REQUEST_BODY, REQUEST_BASE64, REQUEST_HEX -> one(bodyLabel(preset, "Request"), requestBody(exchange, preset))
            RESPONSE_BODY, RESPONSE_ORIGINAL_TEXT, RESPONSE_BASE64, RESPONSE_HEX -> one(bodyLabel(preset, "Response"), responseBody(exchange, preset))
            REQUEST_HEADERS_BODY, URL_REQUEST_BODY, URL_REQUEST_HEADERS_BODY -> buildList {
                if (preset != URL_REQUEST_BODY) add(TrafficCopyPart("Request headers", headerText(request.head.headers)))
                add(TrafficCopyPart("Request body", requestBody(exchange, preset)))
            }
            RESPONSE_HEADERS_BODY, URL_RESPONSE_BODY, URL_RESPONSE_HEADERS_BODY -> buildList {
                add(TrafficCopyPart("Status", status(exchange)))
                if (preset != URL_RESPONSE_BODY) add(TrafficCopyPart("Response headers", response?.let { headerText(it.head.headers) } ?: NO_RESPONSE))
                add(TrafficCopyPart("Response body", responseBody(exchange, preset)))
            }
            REQUEST_RESPONSE_BODIES, REQUEST_RESPONSE_HEADERS_BODIES -> buildList {
                if (preset == REQUEST_RESPONSE_HEADERS_BODIES) add(TrafficCopyPart("Request headers", headerText(request.head.headers)))
                add(TrafficCopyPart("Request body", requestBody(exchange, preset)))
                add(TrafficCopyPart("Status", status(exchange)))
                if (preset == REQUEST_RESPONSE_HEADERS_BODIES) add(TrafficCopyPart("Response headers", response?.let { headerText(it.head.headers) } ?: NO_RESPONSE))
                add(TrafficCopyPart("Response body", responseBody(exchange, preset)))
            }
            GRAPHQL_QUERY, GRAPHQL_VARIABLES, GRAPHQL_EXTENSIONS, GRAPHQL_OPERATION, GRAPHQL_REQUEST -> graphQL(exchange, preset)
            WEBSOCKET_HANDSHAKE -> if (isWebSocket(exchange)) listOf(
                TrafficCopyPart("Request handshake headers", headerText(request.head.headers)), TrafficCopyPart("Response handshake headers", response?.let { headerText(it.head.headers) } ?: NO_RESPONSE))
                else unavailable("Not a captured WebSocket handshake")
            WEBSOCKET_MESSAGES, WEBSOCKET_CLIENT_MESSAGES, WEBSOCKET_SERVER_MESSAGES -> framed(exchange, preset, MessageProtocolId.WEBSOCKET)
            GRPC_MESSAGES -> framed(exchange, preset, MessageProtocolId.GRPC)
            SSE_EVENTS, SSE_DATA -> framed(exchange, preset, MessageProtocolId.SSE)
            GRPC_METHOD -> grpcMethod(exchange)
            GRPC_METADATA -> if (isGrpc(exchange)) listOf(TrafficCopyPart("Request metadata", headerText(request.head.headers)),
                TrafficCopyPart("Response metadata", response?.let { headerText(it.head.headers) } ?: NO_RESPONSE),
                TrafficCopyPart("Request trailers", headerText(request.trailers)), TrafficCopyPart("Response trailers", response?.let { headerText(it.trailers) } ?: NO_RESPONSE))
                else unavailable("Not a captured gRPC request")
            CONNECTION -> connection(exchange)
            TIMINGS -> timings(exchange.timings)
            FAILURE_CLOSURE -> listOf(TrafficCopyPart("State", label(exchange.state.name)), TrafficCopyPart("Termination", exchange.terminalOutcome?.reason?.code?.value ?: "[No recorded failure or closure reason]"))
            CURL -> one("cURL", TrafficCopyCurl.render(exchange, content))
            STRUCTURED_JSON -> buildList {
                addAll(summary(exchange)); addAll(connection(exchange)); addAll(timings(exchange.timings))
                add(TrafficCopyPart("Request parameters", pairs(TrafficCopySupport.parameters(url))))
                add(TrafficCopyPart("Request headers", headerText(request.head.headers))); add(TrafficCopyPart("Request body", requestBody(exchange, preset)))
                add(TrafficCopyPart("Request trailers", headerText(request.trailers)))
                add(TrafficCopyPart("Response headers", response?.let { headerText(it.head.headers) } ?: NO_RESPONSE)); add(TrafficCopyPart("Response body", responseBody(exchange, preset)))
                add(TrafficCopyPart("Response trailers", response?.let { headerText(it.trailers) } ?: NO_RESPONSE))
                annotations(exchange.id).filter { it.state == com.devuloopers.knet.traffic.inspection.InspectionAnnotationState.COMPLETED }.mapNotNull { it.document }.forEach { document ->
                    document.fields.forEach { add(TrafficCopyPart("${document.title}: ${it.label}", it.value)) }
                }
                addAll(framed(exchange, preset, null))
            }
            FULL -> error("Full details use the original evidence renderer")
        }
    }

    private suspend fun requestBody(exchange: HttpExchangeSnapshot, preset: TrafficCopyPreset): String =
        body(exchange.request.body, exchange.request.head.headers, preset, exchange.state in setOf(ExchangeState.REQUEST_HEADERS, ExchangeState.REQUEST_STREAMING))
    private suspend fun responseBody(exchange: HttpExchangeSnapshot, preset: TrafficCopyPreset): String = exchange.response?.let {
        body(it.body, it.head.headers, preset, !exchange.state.isTerminal)
    } ?: NO_RESPONSE
    private suspend fun body(ref: MessageBodyRef, headers: List<HeaderField>, preset: TrafficCopyPreset, streaming: Boolean): String {
        if (ref == MessageBodyRef.Empty) return if (streaming) "[Body not finalized at copy time]" else "[Empty body]"
        return content.body(ref, headers.map { it.name.value to it.value }, format = when (preset) {
            RESPONSE_ORIGINAL_TEXT -> TrafficCopyBodyFormat.ORIGINAL_TEXT
            REQUEST_BASE64, RESPONSE_BASE64 -> TrafficCopyBodyFormat.BASE64
            REQUEST_HEX, RESPONSE_HEX -> TrafficCopyBodyFormat.HEX
            else -> TrafficCopyBodyFormat.TEXT
        }).text ?: "[Empty body]"
    }
    private fun bodyLabel(preset: TrafficCopyPreset, direction: String): String = "$direction body" + when (preset) {
        REQUEST_BASE64, RESPONSE_BASE64 -> " (Base64, captured bytes)"
        REQUEST_HEX, RESPONSE_HEX -> " (hexadecimal, captured bytes)"
        else -> ""
    }

    private suspend fun graphQL(exchange: HttpExchangeSnapshot, preset: TrafficCopyPreset): List<TrafficCopyPart> {
        val request = exchange.request
        val bytes = if (request.head.method == HttpMethod.GET) null else content.body(request.body, request.head.headers.map { it.name.value to it.value })
        val graph = formatting.graphQL(request.head.method.token, request.absoluteUrl(), request.head.headers.firstOrNull { it.name.value.equals("content-type", true) }?.value.orEmpty(), bytes?.decoded?.encodeToByteArray(), bytes?.complete ?: true)
            ?: return unavailable("No captured GraphQL document available")
        val documents = if (graph.source == GraphQLHttpSource.JSON_BATCH) graph.batchEntries else listOf(graph)
        if (documents.isEmpty()) return unavailable("Individual GraphQL batch documents are unavailable")
        return documents.flatMapIndexed { index, document ->
            graphQLParts(document, preset).map { part -> if (documents.size > 1) part.copy(name = "Operation ${index + 1}: ${part.name}") else part }
        } + bytes?.note?.let { listOf(TrafficCopyPart("Body availability", it)) }.orEmpty()
    }

    private fun graphQLParts(graph: com.devuloopers.knet.domain.graphql.GraphQLHttpDocument, preset: TrafficCopyPreset): List<TrafficCopyPart> = buildList {
            if (preset == GRAPHQL_QUERY || preset == GRAPHQL_REQUEST) add(TrafficCopyPart("Query", graph.formattedQuery.ifEmpty { "[Query document unavailable; ${graph.queryProvenance.label}]" }))
            if (preset == GRAPHQL_VARIABLES || preset == GRAPHQL_REQUEST) add(TrafficCopyPart("Variables", graph.variablesJson.takeIf(String::isNotBlank)?.let(formatting::json) ?: "[No captured variables]"))
            if (preset == GRAPHQL_EXTENSIONS || preset == GRAPHQL_REQUEST) add(TrafficCopyPart("Extensions", graph.extensionsJson.takeIf(String::isNotBlank)?.let(formatting::json) ?: "[No captured extensions]"))
            if (preset == GRAPHQL_QUERY && graph.queryProvenance != com.devuloopers.knet.domain.graphql.GraphQLQueryProvenance.REQUEST)
                add(TrafficCopyPart("Query source", graph.queryProvenance.label))
            if (preset == GRAPHQL_OPERATION || preset == GRAPHQL_REQUEST) {
                add(TrafficCopyPart("Operation name", graph.selectedOperation?.name ?: graph.suppliedOperationName ?: "[Anonymous or unavailable]"))
                add(TrafficCopyPart("Operation type", graph.selectedOperation?.type?.label ?: "[Unavailable]"))
                add(TrafficCopyPart("Query source", graph.queryProvenance.label))
            }
            graph.diagnostic?.let { add(TrafficCopyPart("GraphQL note", it.description)) }
        }

    private suspend fun framed(exchange: HttpExchangeSnapshot, preset: TrafficCopyPreset, protocol: MessageProtocolId?): List<TrafficCopyPart> {
        val page = messages(exchange.id)
        val sampled = page.items.take(TrafficCopyLimits.MESSAGES)
        return buildList {
            var matched = 0
            for (message in sampled.sortedBy { it.sequence }) {
                currentCoroutineContext().ensureActive()
                if (message.exchangeId != exchange.id || exchange.connectionId != null && message.connectionId != exchange.connectionId) {
                    add(TrafficCopyPart("Message availability", "[Unavailable: message parent does not match the copied exchange]")); continue
                }
                if (protocol != null && message.protocol != protocol) continue
                if (preset == WEBSOCKET_CLIENT_MESSAGES && message.direction != TrafficDirection.CLIENT_TO_SERVER || preset == WEBSOCKET_SERVER_MESSAGES && message.direction != TrafficDirection.SERVER_TO_CLIENT) continue
                matched++
                val raw = content.raw(message.body, TrafficCopyLimits.MESSAGE)
                val presentation = raw.bytes?.takeIf { raw.complete && message.state == ProtocolMessageState.COMPLETE }?.let { presentations.decode(ProtocolMessagePayloadInput(exchange, message, it,
                    truncated = !raw.complete || message.state != ProtocolMessageState.COMPLETE)) }
                val value = if (preset == SSE_DATA) {
                    if (!raw.complete || message.state != ProtocolMessageState.COMPLETE) raw.note ?: "[Incomplete SSE record; data is unavailable]"
                    else presentation?.dataText?.let(formatting::json) ?: "[No data field or complete SSE decoder result available]"
                } else {
                    presentation?.text?.let(formatting::json) ?: run {
                        val headers = listOf("Content-Type" to if (message.kind == ProtocolMessageKind.BINARY || message.protocol == MessageProtocolId.GRPC) "application/octet-stream" else "text/plain") +
                            message.compressionEncoding?.takeIf { message.compressed }?.let { listOf("Content-Encoding" to it) }.orEmpty()
                        // This path decodes the bytes already read; it never reads the same message body twice.
                        contentText(raw, message, headers)
                    }
                }
                add(TrafficCopyPart("Message ${message.sequence} (${message.protocol.value}/${message.kind.value}, ${label(message.direction.name)}, ${label(message.state.name)})", value))
                message.terminationReason?.let { add(TrafficCopyPart("Message ${message.sequence} termination", it.code.value)) }
            }
            if (matched == 0) add(TrafficCopyPart("Messages", "[No matching captured messages in the copied page]"))
            if (page.nextCursor != null || page.totalCount > sampled.size || page.items.size > sampled.size)
                add(TrafficCopyPart("Message availability", "[Only the first ${sampled.size} of ${maxOf(page.totalCount, page.items.size.toLong())} captured messages were considered]"))
        }
    }

    private fun contentText(raw: TrafficCopyBytes, message: ProtocolMessageSnapshot, headers: List<Pair<String, String>>): String {
        val bytes = raw.bytes ?: return raw.note ?: "[Unavailable payload]"
        val decoded = com.devuloopers.knet.domain.clientNetwork.decoder.BodyTextDecoder.decode(
            com.devuloopers.knet.domain.clientNetwork.decoder.BodyDecoder.decode(bytes, headers, TrafficCopyLimits.BODY), headers)
        val value = when (decoded) {
            is com.devuloopers.knet.domain.clientNetwork.decoder.DecodedTextResult.Success -> formatting.json(decoded.text)
            else -> "[Binary, unsupported or undecodable payload: ${message.observedBytes} bytes]"
        }
        return listOfNotNull(value, raw.note).joinToString("\n")
    }

    private fun opaque(flow: OpaqueFlowSnapshot, preset: TrafficCopyPreset): List<TrafficCopyPart> = when (preset) {
        DESTINATION -> one("Host and port", TrafficCopySupport.destination(flow.destination.host, flow.destination.port))
        SUMMARY, CONNECTION, STRUCTURED_JSON -> buildList {
            add(TrafficCopyPart("Destination", TrafficCopySupport.destination(flow.destination.host, flow.destination.port)))
            add(TrafficCopyPart("State", label(flow.state.name))); add(TrafficCopyPart("Transport", flow.transport.name)); add(TrafficCopyPart("Security", flow.security.name))
            flow.serverName?.let { add(TrafficCopyPart("Server name", it)) }
            add(TrafficCopyPart("Uploaded", TrafficCopySupport.size(flow.uploadedBytes))); add(TrafficCopyPart("Downloaded", TrafficCopySupport.size(flow.downloadedBytes)))
            flow.completedAtEpochMillis?.let { add(TrafficCopyPart("Duration", "${it - flow.startedAtEpochMillis} ms")) }
            if (preset != SUMMARY) {
                add(TrafficCopyPart("Offered ALPN", flow.offeredApplicationProtocols.joinToString(", ").ifEmpty { "[Unavailable]" }))
                add(TrafficCopyPart("Offered TLS versions", flow.offeredTlsVersions.joinToString(", ").ifEmpty { "[Unavailable]" }))
                addAll(opaque(flow, FAILURE_CLOSURE))
                add(TrafficCopyPart("Payload", "[HTTP headers and bodies are unavailable for this opaque connection]"))
            }
        }
        TIMINGS -> one("Connection duration", flow.completedAtEpochMillis?.let { "${it - flow.startedAtEpochMillis} ms" } ?: "[Connection is still active; duration is not final]")
        FAILURE_CLOSURE -> buildList {
            flow.inspectionFailureReason?.let { add(TrafficCopyPart("Inspection failure", label(it.name))) }
            flow.closure?.let { add(TrafficCopyPart("Closed by", label(it.initiator.name))); add(TrafficCopyPart("Close reason", label(it.reason.name))) }
            flow.terminalOutcome?.reason?.let { add(TrafficCopyPart("Termination", it.code.value)) }
            if (isEmpty()) add(TrafficCopyPart("Failure and closure", "[No recorded failure or closure evidence]"))
        }
        else -> unavailable("HTTP URL, request, response and application messages are unavailable for this opaque connection")
    }
    private fun summary(exchange: HttpExchangeSnapshot): List<TrafficCopyPart> = buildList {
        add(TrafficCopyPart("Method", exchange.request.head.method.token)); add(TrafficCopyPart("URL", exchange.request.absoluteUrl())); add(TrafficCopyPart("Status", status(exchange)))
        add(TrafficCopyPart("State", label(exchange.state.name)))
        exchange.terminalOutcome?.reason?.let { add(TrafficCopyPart("Termination", it.code.value)) }
        add(TrafficCopyPart("Time", Instant.fromEpochMilliseconds(exchange.startedAtEpochMillis).toString()))
        add(TrafficCopyPart("Request bytes", byteSize(exchange.request.body)))
        add(TrafficCopyPart("Response bytes", exchange.response?.let { byteSize(it.body) } ?: NO_RESPONSE))
        exchange.timings.totalMillis?.let { add(TrafficCopyPart("Duration", "$it ms")) }
    }
    private fun connection(exchange: HttpExchangeSnapshot): List<TrafficCopyPart> = buildList {
        add(TrafficCopyPart("Client protocol", exchange.request.head.protocol.token)); exchange.response?.let { add(TrafficCopyPart("Upstream protocol", it.head.protocol.token)) }
        exchange.connectionId?.let { add(TrafficCopyPart("Connection ID", it.value)) }; exchange.streamId?.let { add(TrafficCopyPart("Stream ID", it.value.toString())) }
        add(TrafficCopyPart("Origin", exchange.origin.toString()))
    }
    private fun timings(timings: ExchangeTimings): List<TrafficCopyPart> = buildList {
        for ((name, value) in listOf("DNS" to timings.dnsMillis, "Connect" to timings.connectMillis, "TLS" to timings.tlsMillis, "First byte" to timings.firstByteMillis, "Download" to timings.downloadMillis, "Total" to timings.totalMillis))
            value?.let { add(TrafficCopyPart(name, "$it ms")) }
        if (isEmpty()) add(TrafficCopyPart("Timings", "[No recorded timing measurements]"))
        add(TrafficCopyPart("Connection reused", timings.connectionReused.toString()))
    }
    private fun grpcMethod(exchange: HttpExchangeSnapshot): List<TrafficCopyPart> {
        if (!isGrpc(exchange)) return unavailable("Not a captured gRPC request")
        val path = exchange.request.pathWithoutQuery()?.removePrefix("/").orEmpty(); val separator = path.lastIndexOf('/')
        if (separator <= 0 || separator == path.lastIndex) return unavailable("No valid recorded gRPC service and method")
        return listOf(TrafficCopyPart("Service", path.take(separator)), TrafficCopyPart("Method", path.drop(separator + 1)))
    }
    private fun isGrpc(exchange: HttpExchangeSnapshot): Boolean = (exchange.request.head.headers + exchange.response?.head?.headers.orEmpty()).any { it.name.value.equals("content-type", true) && it.value.substringBefore(';').trim().startsWith("application/grpc", true) }
    private fun isWebSocket(exchange: HttpExchangeSnapshot): Boolean = exchange.response?.head?.status?.code == 101 || exchange.request.head.headers.any {
        it.name.value.equals("sec-websocket-version", true) || it.name.value.equals("sec-websocket-key", true) ||
        it.name.value.equals("upgrade", true) && it.value.equals("websocket", true) || it.name.value.equals(":protocol", true) && it.value.equals("websocket", true)
    }
    private fun byteSize(body: MessageBodyRef): String = if (body is MessageBodyRef.Unavailable) "[Body size unavailable]" else TrafficCopySupport.size(TrafficCopySupport.observed(body))
    private fun headerText(headers: List<HeaderField>): String = pairs(headers.map { it.name.value to it.value }).ifEmpty { "[No captured headers]" }
    private fun cookieText(headers: List<HeaderField>, name: String): String = headers.filter { it.name.value.equals(name, true) }.joinToString("\n") { safe(it.value) }.ifEmpty { "[No captured cookies]" }
    private fun status(exchange: HttpExchangeSnapshot): String = exchange.response?.head?.let { "${it.status.code}${it.reasonPhrase?.let { phrase -> " $phrase" }.orEmpty()}" } ?: "[No response captured; ${label(exchange.state.name)}]"
    private fun one(name: String, value: String): List<TrafficCopyPart> = listOf(TrafficCopyPart(name, value))
    private fun unavailable(reason: String): List<TrafficCopyPart> = one("Availability", "[$reason]")
    private fun title(record: TrafficCopyRecord): String = record.exchange?.let { "${it.request.head.method.token} ${it.request.pathWithoutQuery() ?: it.request.absoluteUrl()}" }
        ?: record.opaque?.let { "${it.security.name} connection" } ?: "Unavailable item (${record.key})"

    private companion object {
        const val NO_RESPONSE = "[No response captured]"
        val ADDRESS_LINES = setOf(URL, DESTINATION, PATH_QUERY)
        val COMPONENTS = setOf(QUERY_PARAMETERS, REQUEST_HEADERS, REQUEST_COOKIES, REQUEST_BODY, REQUEST_BASE64, REQUEST_HEX,
            RESPONSE_HEADERS, RESPONSE_COOKIES, RESPONSE_BODY, RESPONSE_ORIGINAL_TEXT, RESPONSE_BASE64, RESPONSE_HEX, GRAPHQL_QUERY, GRAPHQL_VARIABLES, GRAPHQL_EXTENSIONS)
    }
}
