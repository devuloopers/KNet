package com.devuloopers.knet.ui.desktop.httppanel.copy

import com.devuloopers.knet.application.contract.traffic.TrafficCopyPreset
import com.devuloopers.knet.application.contract.traffic.TrafficCopyPreset.*
import com.devuloopers.knet.ui.core.components.menu.*

/** Contextual protocol groups shared by Traffic and API Studio copy menus. */
enum class CaptureCopyProtocol { GRAPHQL, WEBSOCKET, GRAPHQL_WEBSOCKET, GRPC, SSE, OPAQUE }

/** Body-free UI eligibility; the application validates the actual frozen source on invocation. */
data class CaptureCopyMenuCapabilities(
    /** At least one HTTP request is represented. */ val hasHttp: Boolean,
    /** At least one response is represented. */ val hasResponse: Boolean,
    /** At least one ordinary HTTP request can potentially be represented as cURL. */ val canCurl: Boolean,
    /** Only represented semantic protocol groups. */ val protocols: Set<CaptureCopyProtocol>,
) { companion object }

/** Shared labelled preset catalogue. The caller owns and freezes its source/selection in [copy]. */
fun captureCopyMenuItems(capabilities: CaptureCopyMenuCapabilities, studio: Boolean = false,
    copy: (TrafficCopyPreset) -> Unit): List<ContextMenuEntry> {
    fun item(label: String, preset: TrafficCopyPreset, enabled: Boolean = true): ContextMenuItem = ContextMenuItem(label, enabled = enabled) { copy(preset) }
    val protocolGroups = capabilities.protocols.map { protocol -> when (protocol) {
        CaptureCopyProtocol.GRAPHQL -> "GraphQL" to listOf(item("Query", GRAPHQL_QUERY), item("Variables", GRAPHQL_VARIABLES), item("Extensions", GRAPHQL_EXTENSIONS), item("Operation details", GRAPHQL_OPERATION), item("Complete GraphQL request", GRAPHQL_REQUEST))
        CaptureCopyProtocol.WEBSOCKET, CaptureCopyProtocol.GRAPHQL_WEBSOCKET -> (if (protocol == CaptureCopyProtocol.WEBSOCKET) "WebSocket" else "GraphQL WebSocket") to listOf(
            item("Messages in both directions", WEBSOCKET_MESSAGES), item("Client messages", WEBSOCKET_CLIENT_MESSAGES), item("Server messages", WEBSOCKET_SERVER_MESSAGES), item("Handshake details", WEBSOCKET_HANDSHAKE))
        CaptureCopyProtocol.GRPC -> "gRPC" to listOf(item("Service and method", GRPC_METHOD), item("Metadata and trailers", GRPC_METADATA), item("Messages", GRPC_MESSAGES))
        CaptureCopyProtocol.SSE -> "SSE" to listOf(item("Events", SSE_EVENTS), item("Event data", SSE_DATA))
        CaptureCopyProtocol.OPAQUE -> "Opaque connection" to listOf(item("Destination", DESTINATION), item("Connection metadata", CONNECTION), item("Failure and closure details", FAILURE_CLOSURE))
    } }
    return buildList {
        add(item(if (studio) "Full exchange details" else "Full traffic details", FULL)); add(item("Summary", SUMMARY)); add(ContextMenuSeparator)
        add(ContextMenuSubmenu("Address", listOf(item("Full URL", URL, capabilities.hasHttp), item("Host and port", DESTINATION),
            item("Path and query", PATH_QUERY, capabilities.hasHttp), item("Query parameters", QUERY_PARAMETERS, capabilities.hasHttp))))
        add(ContextMenuSubmenu("Request", listOf(item("Headers", REQUEST_HEADERS), item("Cookies", REQUEST_COOKIES), item("Body", REQUEST_BODY),
            item("Headers and body", REQUEST_HEADERS_BODY), item("Body as Base64", REQUEST_BASE64), item("Body as hexadecimal", REQUEST_HEX)), enabled = capabilities.hasHttp))
        add(ContextMenuSubmenu("Response", listOf(item("Headers", RESPONSE_HEADERS), item("Cookies", RESPONSE_COOKIES), item("Body", RESPONSE_BODY),
            item("Original text", RESPONSE_ORIGINAL_TEXT), item("Headers and body", RESPONSE_HEADERS_BODY), item("Body as Base64", RESPONSE_BASE64), item("Body as hexadecimal", RESPONSE_HEX)), enabled = capabilities.hasResponse))
        add(ContextMenuSubmenu("Combinations", listOf(item("URL and request body", URL_REQUEST_BODY), item("URL and request with headers", URL_REQUEST_HEADERS_BODY),
            item("URL and response body", URL_RESPONSE_BODY, capabilities.hasResponse), item("URL and response with headers", URL_RESPONSE_HEADERS_BODY, capabilities.hasResponse),
            item("Request and response bodies", REQUEST_RESPONSE_BODIES, capabilities.hasResponse), item("Request and response with headers", REQUEST_RESPONSE_HEADERS_BODIES, capabilities.hasResponse)), enabled = capabilities.hasHttp))
        if (protocolGroups.isNotEmpty()) add(ContextMenuSubmenu("Protocol details", if (protocolGroups.size == 1) protocolGroups.single().second
            else protocolGroups.map { (label, children) -> ContextMenuSubmenu(label, children) }))
        add(ContextMenuSubmenu("Connection", listOf(item("Connection details", CONNECTION), item("Timing details", TIMINGS), item("Failure and closure details", FAILURE_CLOSURE))))
        add(ContextMenuSubmenu("Developer formats", listOf(item("cURL", CURL, capabilities.canCurl), item("Structured JSON", STRUCTURED_JSON))))
    }
}
