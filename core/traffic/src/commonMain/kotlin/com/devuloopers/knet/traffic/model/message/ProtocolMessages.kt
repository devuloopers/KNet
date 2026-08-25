package com.devuloopers.knet.traffic.model.message

import com.devuloopers.knet.traffic.id.ConnectionId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.ProtocolMessageId
import com.devuloopers.knet.traffic.id.StreamId
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.body.MessageBodyRef

/** Extension-safe identifier for a framed application protocol. */
@JvmInline
public value class MessageProtocolId(public val value: String) {
    init {
        require(value.isNotBlank()) { "MessageProtocolId must not be blank." }
    }

    public companion object {
        /** Native gRPC over HTTP/2. */
        public val GRPC: MessageProtocolId = MessageProtocolId("grpc")

        /** RFC 6455 WebSocket messages after a successful HTTP upgrade. */
        public val WEBSOCKET: MessageProtocolId = MessageProtocolId("websocket")

        /** Server-Sent Events records carried by an HTTP response stream. */
        public val SSE: MessageProtocolId = MessageProtocolId("sse")
    }
}

/** Extension-safe semantic kind of one framed protocol message. */
@JvmInline
public value class ProtocolMessageKind(public val value: String) {
    init {
        require(value.isNotBlank()) { "ProtocolMessageKind must not be blank." }
    }

    public companion object {
        /** A data-bearing gRPC message. */
        public val DATA: ProtocolMessageKind = ProtocolMessageKind("data")

        /** A UTF-8 WebSocket data message. */
        public val TEXT: ProtocolMessageKind = ProtocolMessageKind("text")

        /** An opaque binary WebSocket data message. */
        public val BINARY: ProtocolMessageKind = ProtocolMessageKind("binary")

        /** A WebSocket ping control message. */
        public val PING: ProtocolMessageKind = ProtocolMessageKind("ping")

        /** A WebSocket pong control message. */
        public val PONG: ProtocolMessageKind = ProtocolMessageKind("pong")

        /** A WebSocket close control message containing an optional code and reason. */
        public val CLOSE: ProtocolMessageKind = ProtocolMessageKind("close")

        /** One application-protocol record whose finer semantics are derived by its decoder. */
        public val RECORD: ProtocolMessageKind = ProtocolMessageKind("record")
    }
}

/** Durable lifecycle of a framed protocol message. */
public enum class ProtocolMessageState {
    IN_PROGRESS,
    COMPLETE,
    TRUNCATED,
    FAILED,
    CANCELLED,
}

/**
 * Immutable framed-message metadata shared by Traffic, breakpoints, API Studio, and inspectors.
 *
 * The payload remains owned by the common body store and is read through the same bounded body
 * access port as HTTP request and response bodies.
 */
public data class ProtocolMessageSnapshot(
    public val id: ProtocolMessageId,
    public val connectionId: ConnectionId,
    public val exchangeId: ExchangeId,
    public val streamId: StreamId?,
    public val protocol: MessageProtocolId,
    public val kind: ProtocolMessageKind,
    public val direction: TrafficDirection,
    public val sequence: Long,
    public val occurredAtEpochMillis: Long,
    public val declaredBytes: Long?,
    public val observedBytes: Long,
    public val compressed: Boolean,
    public val compressionEncoding: String?,
    public val body: MessageBodyRef,
    public val state: ProtocolMessageState,
    public val errorCode: String? = null,
) {
    init {
        require(sequence >= 0L) { "Protocol message sequence must not be negative." }
        require(occurredAtEpochMillis >= 0L) { "Protocol message timestamp must not be negative." }
        require(declaredBytes == null || declaredBytes >= 0L) { "Declared message bytes must not be negative." }
        require(observedBytes >= 0L) { "Observed message bytes must not be negative." }
        require(compressionEncoding == null || compressionEncoding.isNotBlank()) {
            "Compression encoding must not be blank."
        }
        require(errorCode == null || errorCode.isNotBlank()) { "Message error code must not be blank." }
    }
}
