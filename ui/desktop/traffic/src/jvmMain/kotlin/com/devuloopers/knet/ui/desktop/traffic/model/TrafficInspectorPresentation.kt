package com.devuloopers.knet.ui.desktop.traffic.model

import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.application.contract.inspection.GraphQLInspectionMetadata
import com.devuloopers.knet.ui.desktop.httppanel.model.GraphQLQueryAvailability
import com.devuloopers.knet.ui.desktop.httppanel.model.graphQLQueryAvailability
import com.devuloopers.knet.traffic.inspection.InspectionAnnotation
import com.devuloopers.knet.traffic.inspection.InspectionAnnotationState
import com.devuloopers.knet.traffic.inspection.InspectionDocument
import com.devuloopers.knet.traffic.model.ExchangeTerminalOutcome
import com.devuloopers.knet.traffic.model.absoluteUrl
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.RequestTarget
import com.devuloopers.knet.traffic.model.message.MessageProtocolId
import com.devuloopers.knet.ui.core.components.inspector.*

/** Presentation mode derived only from captured protocol evidence or the existing request descriptor. */
internal enum class TrafficInspectorProtocol(val label: String, val streaming: Boolean = false) {
    HTTP("HTTP"), GRAPHQL("GraphQL"), GRPC("gRPC", true), WEBSOCKET("WS", true),
    GRAPHQL_WEBSOCKET("GraphQL WS", true), SSE("SSE", true),
}

/** Standard captured gRPC completion codes; invalid or missing tokens never receive an OK fallback. */
private enum class CapturedGrpcStatus(val code: Int) {
    OK(0), CANCELLED(1), UNKNOWN(2), INVALID_ARGUMENT(3), DEADLINE_EXCEEDED(4), NOT_FOUND(5),
    ALREADY_EXISTS(6), PERMISSION_DENIED(7), RESOURCE_EXHAUSTED(8), FAILED_PRECONDITION(9),
    ABORTED(10), OUT_OF_RANGE(11), UNIMPLEMENTED(12), INTERNAL(13), UNAVAILABLE(14), DATA_LOSS(15), UNAUTHENTICATED(16),
}

/** Small immutable overview, independent from body buffers, UI state and protocol-engine implementations. */
internal data class TrafficInspectorPresentation(
    val protocol: TrafficInspectorProtocol,
    val summary: KNetInspectorSummary,
    val connection: List<KNetInspectorField>,
    val protocolFields: List<KNetInspectorField>,
    val documents: List<InspectionDocument>,
    val trailers: List<KNetInspectorField>,
    val technical: List<KNetInspectorField>,
    val failure: String?,
    val analysisUnavailable: Boolean,
    val graphQLQueryAvailability: GraphQLQueryAvailability? = null,
) {
    val messagesTitle: String get() = if (protocol == TrafficInspectorProtocol.SSE) "Events" else "Messages"
}

/** Rejects stale cross-selection message pages and their payload previews at the presentation boundary. */
internal fun ProtocolMessagesUiState.forSelection(exchangeId: String): ProtocolMessagesUiState =
    takeIf { this.exchangeId == exchangeId && items.all { it.exchangeId.value == exchangeId } }
        ?: ProtocolMessagesUiState(exchangeId = exchangeId, isLoading = exchangeId.isNotBlank())

/**
 * Builds protocol-aware display facts without parsing payloads or inferring operation success from transport status.
 * Failed/skipped analyzers never become protocol claims. All cached details are fenced by exchange identity.
 */
internal fun inspectorPresentation(row: TrafficRowUiState, prepared: InspectorPreparedState,
    messages: ProtocolMessagesUiState): TrafficInspectorPresentation {
    val details = prepared.forSelection(row.transactionId)
    val exchange = details?.exchange?.takeIf { it.id.value == row.transactionId }
    val page = messages.forSelection(row.transactionId)
    val capturedDocuments = details?.annotations.orEmpty().filter { it.exchangeId.value == row.transactionId &&
        it.state == InspectionAnnotationState.COMPLETED }
        .groupBy { it.inspectorId }
        .values.mapNotNull { versions -> versions.maxWithOrNull(compareBy<InspectionAnnotation> { it.schemaVersion }.thenBy { it.createdAtEpochMillis }) }
        .mapNotNull { it.document }
        .filter { it.fields.isNotEmpty() || !it.summary.isNullOrBlank() }
    val responseHeaders = exchange?.response?.head?.headers.orEmpty()
    val contentType = responseHeaders.uniqueHeader("content-type") ?: row.contentType.orEmpty()
    val mime = contentType.substringBefore(';').trim()
    val protocol = when {
        row.requestKind == RequestKindId.GRAPHQL_WEBSOCKET -> TrafficInspectorProtocol.GRAPHQL_WEBSOCKET
        row.status == 101 && responseHeaders.uniqueHeader("sec-websocket-protocol") == "graphql-transport-ws" -> TrafficInspectorProtocol.GRAPHQL_WEBSOCKET
        row.requestKind == RequestKindId.WEBSOCKET || row.status == 101 && responseHeaders.uniqueHeader("upgrade")?.equals("websocket", true) == true -> TrafficInspectorProtocol.WEBSOCKET
        row.requestKind == RequestKindId.GRPC || mime.equals("application/grpc", true) || mime.startsWith("application/grpc+", true) || page.items.any { it.protocol == MessageProtocolId.GRPC } -> TrafficInspectorProtocol.GRPC
        mime.equals("text/event-stream", true) || page.items.any { it.protocol == MessageProtocolId.SSE } -> TrafficInspectorProtocol.SSE
        row.requestKind == RequestKindId.GRAPHQL || capturedDocuments.any { it.kind == GraphQLInspectionMetadata.DOCUMENT_KIND } -> TrafficInspectorProtocol.GRAPHQL
        else -> TrafficInspectorProtocol.HTTP
    }
    val outcome = row.terminalOutcome ?: exchange?.terminalOutcome
    val status = exchange?.response?.head?.status?.code ?: row.status
    val statusText = exchange?.response?.head?.reasonPhrase ?: row.statusText
    val httpValue = if (status > 0) "$status $statusText".trim() else if (outcome == null) "Pending" else "No response"
    val httpTone = when { status in 200..299 -> KNetInspectorTone.SUCCESS; status in 400..599 -> KNetInspectorTone.ERROR
        status in 300..399 -> KNetInspectorTone.WARNING; else -> KNetInspectorTone.NEUTRAL }
    val lifecycle = when (outcome) {
        is ExchangeTerminalOutcome.Failed -> "Failed"
        is ExchangeTerminalOutcome.Cancelled -> "Cancelled"
        is ExchangeTerminalOutcome.Dropped -> "Blocked"
        ExchangeTerminalOutcome.Completed -> if (protocol == TrafficInspectorProtocol.WEBSOCKET || protocol == TrafficInspectorProtocol.GRAPHQL_WEBSOCKET) "Closed" else "Completed"
        null -> when {
            protocol == TrafficInspectorProtocol.SSE && status in 200..299 -> "Streaming"
            (protocol == TrafficInspectorProtocol.WEBSOCKET || protocol == TrafficInspectorProtocol.GRAPHQL_WEBSOCKET) && status == 101 -> "Active"
            status > 0 -> "Not upgraded"
            else -> "Connecting"
        }
    }
    val lifecycleTone = when (outcome) {
        is ExchangeTerminalOutcome.Failed -> KNetInspectorTone.ERROR
        is ExchangeTerminalOutcome.Cancelled, is ExchangeTerminalOutcome.Dropped -> KNetInspectorTone.WARNING
        else -> KNetInspectorTone.INFO
    }
    val trailers = exchange?.response?.trailers.orEmpty()
    val grpcHeaders = trailers.ifEmpty { responseHeaders }
    val grpcValue = grpcHeaders.uniqueHeader("grpc-status")?.takeIf { raw -> raw.all { it in '0'..'9' } }
        ?.toIntOrNull()?.let { code -> CapturedGrpcStatus.entries.firstOrNull { it.code == code } }
    val rpc = KNetInspectorMetric("RPC status", grpcValue?.let { "${it.code} ${it.name}" }
        ?: if (outcome != null) "Unavailable" else "Pending", when {
            grpcValue == null -> if (outcome != null) KNetInspectorTone.WARNING else KNetInspectorTone.NEUTRAL
            grpcValue != CapturedGrpcStatus.OK -> KNetInspectorTone.ERROR
            outcome is ExchangeTerminalOutcome.Failed -> KNetInspectorTone.ERROR
            outcome is ExchangeTerminalOutcome.Cancelled || outcome is ExchangeTerminalOutcome.Dropped -> KNetInspectorTone.WARNING
            else -> KNetInspectorTone.SUCCESS
        })
    val metrics = when (protocol) {
        TrafficInspectorProtocol.WEBSOCKET, TrafficInspectorProtocol.GRAPHQL_WEBSOCKET -> listOf(
            KNetInspectorMetric("State", if (status != 101 && (status > 0 || outcome == ExchangeTerminalOutcome.Completed) &&
                (outcome == null || outcome == ExchangeTerminalOutcome.Completed)) "Not upgraded" else lifecycle,
                if (status != 101 && status > 0 && (outcome == null || outcome == ExchangeTerminalOutcome.Completed))
                    if (status >= 400) KNetInspectorTone.ERROR else KNetInspectorTone.WARNING else lifecycleTone),
            KNetInspectorMetric("Handshake", httpValue, httpTone), KNetInspectorMetric("Messages", page.totalCount.toString()))
        TrafficInspectorProtocol.SSE -> listOf(KNetInspectorMetric("State", lifecycle, lifecycleTone),
            KNetInspectorMetric("HTTP status", httpValue, httpTone), KNetInspectorMetric("Records", page.totalCount.toString()))
        TrafficInspectorProtocol.GRPC -> listOf(rpc, KNetInspectorMetric("HTTP status", httpValue, httpTone),
            KNetInspectorMetric("Duration", row.formattedTime))
        else -> listOf(KNetInspectorMetric("Status", if (outcome is ExchangeTerminalOutcome.Failed || outcome is ExchangeTerminalOutcome.Cancelled || outcome is ExchangeTerminalOutcome.Dropped) lifecycle else httpValue,
            if (outcome is ExchangeTerminalOutcome.Failed) KNetInspectorTone.ERROR else if (outcome is ExchangeTerminalOutcome.Cancelled || outcome is ExchangeTerminalOutcome.Dropped) KNetInspectorTone.WARNING else httpTone),
            KNetInspectorMetric("Duration", row.formattedTime), KNetInspectorMetric("Size", row.formattedSize))
    }
    val url = exchange?.request?.absoluteUrl()?.takeIf { "://" in it } ?: row.fullUrl
    val path = when (val target = exchange?.request?.head?.target) {
        is RequestTarget.Absolute -> target.pathAndQuery; is RequestTarget.Origin -> target.pathAndQuery; else -> row.path
    }
    val protocolFields = buildList {
        if (protocol == TrafficInspectorProtocol.GRPC) {
            val parts = path.substringBefore('?').trimStart('/').split('/')
            if (parts.size == 2 && parts.all(String::isNotBlank)) {
                add(KNetInspectorField("Service", parts[0], true)); add(KNetInspectorField("Method", parts[1], true))
            }
        }
        if (protocol == TrafficInspectorProtocol.WEBSOCKET || protocol == TrafficInspectorProtocol.GRAPHQL_WEBSOCKET) {
            responseHeaders.uniqueHeader("sec-websocket-protocol")?.let { add(KNetInspectorField("Subprotocol", it, true)) }
        }
    }
    // Adapt existing persisted documents for Overview. Keep the full query in its source viewer,
    // and preserve the original annotations for matching, export and other consumers.
    val documents = capturedDocuments.map { document ->
        if (document.kind == GraphQLInspectionMetadata.DOCUMENT_KIND) document.copy(summary = null,
            fields = document.fields.filterNot { it.label == GraphQLInspectionMetadata.REQUEST_TARGET_FIELD })
        else document
    }
    val queryAvailability = if (protocol != TrafficInspectorProtocol.GRAPHQL) null else when {
        details?.isPreparing == true -> GraphQLQueryAvailability.PREPARING
        exchange == null || details?.loadState != InspectorLoadState.READY -> GraphQLQueryAvailability.UNAVAILABLE
        else -> (if (exchange.request.head.method.token.equals("GET", true)) details.requestParameterSpec
            else details.requestPayloadSpec).graphQLQueryAvailability
    }
    return TrafficInspectorPresentation(protocol,
        KNetInspectorSummary(if (protocol == TrafficInspectorProtocol.HTTP) row.method else protocol.label,
            row.host, url, url, metrics, if (protocol == TrafficInspectorProtocol.HTTP && row.method == "GET") KNetInspectorTone.SUCCESS else KNetInspectorTone.INFO),
        buildList {
            add(KNetInspectorField("Client protocol", exchange?.request?.head?.protocol?.token ?: row.clientProtocol.token, true))
            add(KNetInspectorField("Upstream protocol", exchange?.response?.head?.protocol?.token ?: row.upstreamProtocol?.token ?: "Pending", true))
            add(KNetInspectorField("Source", exchange?.origin?.displayName ?: row.origin.displayName))
            if (contentType.isNotBlank()) add(KNetInspectorField("Content type", contentType, true))
            add(KNetInspectorField("Time", row.formattedTimestamp, true))
        }, protocolFields, documents, trailers.map { KNetInspectorField(it.name.value, it.value, true) },
        buildList {
            (exchange?.connectionId?.value ?: row.connectionId)?.let { add(KNetInspectorField("Connection ID", it, true)) }
            (exchange?.streamId?.value ?: row.streamId)?.let { add(KNetInspectorField("Stream ID", it.toString(), true)) }
            add(KNetInspectorField("Transport method", row.method, true))
            if (protocol.streaming) add(KNetInspectorField("Duration", row.formattedTime))
            row.appliedNetworkCondition?.let { condition ->
                add(KNetInspectorField("Network condition", condition.profileId, true))
                condition.ruleId?.let { add(KNetInspectorField("Condition rule", it, true)) }
            }
            outcome?.reason?.code?.value?.let { add(KNetInspectorField("Termination", it, true)) }
            if (protocol == TrafficInspectorProtocol.GRPC && grpcValue == null && outcome != null)
                add(KNetInspectorField("RPC status", "Not captured"))
        }, (outcome as? ExchangeTerminalOutcome.Failed)?.reason?.code?.value,
        details?.annotations.orEmpty().any { it.exchangeId.value == row.transactionId && it.state == InspectionAnnotationState.FAILED },
        graphQLQueryAvailability = queryAvailability)
}

/** Ambiguous duplicate headers do not establish a protocol or a final RPC status. */
private fun List<HeaderField>.uniqueHeader(name: String): String? = filter { it.name.value.equals(name, true) }
    .singleOrNull()?.value?.trim()?.takeIf(String::isNotEmpty)
