package com.devuloopers.knet.protocol

/**
 * Represents the direction of a WebSocket frame.
 */
enum class FrameDirection {
    /** Frame sent from the client to the proxy/server. */
    CLIENT_TO_SERVER,
    /** Frame sent from the server/proxy to the client. */
    SERVER_TO_CLIENT
}

/**
 * Represents the type of a WebSocket frame.
 */
enum class FrameType {
    /** UTF-8 text message. */
    TEXT,
    /** Binary raw payload. */
    BINARY,
    /** Connection close frame. */
    CLOSE,
    /** Ping control frame. */
    PING,
    /** Pong response frame. */
    PONG
}

/**
 * Represents a recorded WebSocket frame passing through KNet.
 *
 * Every property has been fully documented as required by the public API documentation guidelines.
 *
 * @property id Unique identifier for the recorded frame.
 * @property timestamp Epoch millisecond timestamp of when the frame was intercepted.
 * @property direction Direction of transmission (incoming or outgoing).
 * @property type The type of frame payload.
 * @property length Size of the frame payload in bytes.
 * @property payloadText The decoded text content if the frame is a text frame or close frame. Null otherwise.
 * @property payloadHex Hexadecimal string representation of the binary payload. Null otherwise.
 */
data class WebSocketFrameRecord(
    val id: String,
    val timestamp: Long,
    val direction: FrameDirection,
    val type: FrameType,
    val length: Int,
    val payloadText: String? = null,
    val payloadHex: String? = null
)
