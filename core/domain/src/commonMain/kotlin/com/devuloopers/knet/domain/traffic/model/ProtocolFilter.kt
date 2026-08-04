package com.devuloopers.knet.domain.traffic.model

/**
 * Strongly typed enum representing available traffic protocol filters.
 *
 * @property label User-facing display label for UI rendering.
 */
public enum class ProtocolFilter(val label: String) {
    ALL("All"),
    HTTP("HTTP"),
    HTTPS("HTTPS"),
    WEBSOCKET("WebSocket"),
    HTTP_2("HTTP/2"),
    GRPC("gRPC"),
    OTHER("Other")
}
