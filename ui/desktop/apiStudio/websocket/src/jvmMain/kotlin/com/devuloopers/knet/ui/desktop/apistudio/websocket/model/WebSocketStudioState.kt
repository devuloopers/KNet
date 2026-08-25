package com.devuloopers.knet.ui.desktop.apistudio.websocket.model

import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolExecutionEvent
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolMetadataEntry

/** Presentation-owned kinds supported by the WebSocket message composer. */
enum class WebSocketStudioMessageKind {
    TEXT,
    BINARY_BASE64,
}

/**
 * Immutable presentation state for one WebSocket API Studio draft and live session.
 *
 * @property documentId Common workspace identity, blank while the editor is transient.
 * @property url Authored WebSocket endpoint.
 * @property connectTimeoutMillis Presentation-safe numeric timeout input.
 * @property subprotocols Comma-separated subprotocol preferences.
 * @property headers Optional handshake header rows.
 * @property messageKind Current outbound composer interpretation.
 * @property messageContent Current outbound composer payload.
 * @property events Bounded live-session event timeline.
 * @property selectedEventIndex Optional selected timeline entry.
 * @property isConnecting Whether a handshake is currently pending.
 * @property isConnected Whether an interactive session is available.
 * @property isDirty Whether the workspace payload has unapplied persistence work.
 * @property errorMessage Optional presentation-safe authoring or execution failure.
 */
data class WebSocketStudioState(
    val documentId: String,
    val url: String = "",
    val connectTimeoutMillis: String = "",
    val subprotocols: String = "",
    val headers: List<ApiStudioProtocolMetadataEntry> = emptyList(),
    val messageKind: WebSocketStudioMessageKind = WebSocketStudioMessageKind.TEXT,
    val messageContent: String = "",
    val events: List<ApiStudioProtocolExecutionEvent> = emptyList(),
    val selectedEventIndex: Int? = null,
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val isDirty: Boolean = true,
    val errorMessage: String? = null,
) {
    /** Whether the authored endpoint and timeout are ready for strict engine validation. */
    val canConnect: Boolean
        get() {
            val timeout = connectTimeoutMillis.toLongOrNull()
            return (url.startsWith("ws://") || url.startsWith("wss://")) &&
                (connectTimeoutMillis.isBlank() || timeout != null && timeout in 1L..3_600_000L)
        }

    /** Whether the current composer contains a sendable message. */
    val canSend: Boolean
        get() = isConnected
}
