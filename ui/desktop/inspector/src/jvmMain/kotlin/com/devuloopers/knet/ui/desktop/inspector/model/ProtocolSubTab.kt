package com.devuloopers.knet.ui.desktop.inspector.model

/**
 * Enum representing sub-tabs in [com.devuloopers.knet.ui.desktop.inspector.protocol.ProtocolInspector].
 */
public enum class ProtocolSubTab(public val label: String) {
    HTTP2("HTTP/2"),
    HTTP3("HTTP/3"),
    WEBSOCKET("WebSocket"),
    GRPC("gRPC");

    public companion object {
        /**
         * Resolves matching [ProtocolSubTab] for a given protocol string, defaulting to [HTTP2].
         */
        public fun fromProtocol(protocol: String): ProtocolSubTab {
            return entries.firstOrNull {
                it.label.equals(protocol, ignoreCase = true) ||
                    it.name.equals(protocol, ignoreCase = true)
            } ?: HTTP2
        }
    }
}
