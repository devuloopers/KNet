package com.devuloopers.knet.ui.desktop.apistudio.graphqlwebsocket.model

import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolExecutionEvent
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolMetadataEntry

/** Editable GraphQL subscription document selected in the shared code-editor area. */
enum class GraphQLWebSocketAuthoringTab {
    QUERY,
    VARIABLES,
    EXTENSIONS,
    CONNECTION_PARAMETERS,
}

/**
 * Immutable authoring and execution state for one modern GraphQL WebSocket request.
 *
 * The state remains presentation-owned. Wire validation and lifecycle enforcement stay in
 * `:engine:graphqlWebSocket` behind API Studio application contracts.
 */
data class GraphQLWebSocketStudioState(
    val documentId: String,
    val url: String = "",
    val connectTimeoutMillis: String = "",
    val acknowledgementTimeoutMillis: String = "",
    val headers: List<ApiStudioProtocolMetadataEntry> = emptyList(),
    val connectionParametersJson: String = "",
    val query: String = "",
    val operationName: String = "",
    val variablesJson: String = "",
    val extensionsJson: String = "",
    val operationId: String = "",
    val selectedAuthoringTab: GraphQLWebSocketAuthoringTab = GraphQLWebSocketAuthoringTab.QUERY,
    val events: List<ApiStudioProtocolExecutionEvent> = emptyList(),
    val selectedEventIndex: Int? = null,
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val isDirty: Boolean = true,
    val errorMessage: String? = null,
) {
    /** Whether enough bounded input exists for the engine to perform strict validation. */
    val canConnect: Boolean
        get() {
            val connectTimeout = connectTimeoutMillis.toLongOrNull()
            val acknowledgementTimeout = acknowledgementTimeoutMillis.toLongOrNull()
            return (url.startsWith("ws://") || url.startsWith("wss://")) &&
                query.isNotBlank() &&
                (connectTimeoutMillis.isBlank() || connectTimeout != null && connectTimeout in 1L..3_600_000L) &&
                (acknowledgementTimeoutMillis.isBlank() ||
                    acknowledgementTimeout != null && acknowledgementTimeout in 1L..3_600_000L)
        }
}
