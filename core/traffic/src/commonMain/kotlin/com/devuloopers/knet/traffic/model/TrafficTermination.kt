package com.devuloopers.knet.traffic.model

import com.devuloopers.knet.traffic.model.message.MessageProtocolId

/** Stable persistence-safe identifier for one traffic termination reason. */
@JvmInline
public value class TrafficTerminationCode(public val value: String) {
    init {
        require(CODE_PATTERN.matches(value)) {
            "Traffic termination codes must use 1-128 lowercase ASCII letters, numbers, dots, underscores, or hyphens."
        }
    }

    private companion object {
        val CODE_PATTERN: Regex = Regex("[a-z0-9][a-z0-9._-]{0,127}")
    }
}

/**
 * Semantic reason shared by exchange, connection, body, and framed-message termination.
 *
 * Known cross-protocol reasons are closed enums. Protocol and extension reasons remain
 * namespaced and forward compatible, while [Unknown] preserves values written by newer or legacy
 * KNet versions without leaking raw strings through runtime APIs.
 */
public sealed interface TrafficTerminationReason {
    public val code: TrafficTerminationCode

    /** KNet/proxy lifecycle ended ownership of otherwise active traffic. */
    public enum class Lifecycle(override val code: TrafficTerminationCode) : TrafficTerminationReason {
        PROXY_STOPPED(TrafficTerminationCode("proxy-stopped")),
        PROXY_ENGINE_FAILED(TrafficTerminationCode("proxy-engine-failed")),
        PROCESS_INTERRUPTED(TrafficTerminationCode("process-interrupted")),
        CAPTURE_SESSION_CLOSED(TrafficTerminationCode("capture_session_closed")),
        CAPTURE_TARGET_ROTATED(TrafficTerminationCode("capture_target_rotated")),
    }

    /** Transport-level failure or peer lifecycle event. */
    public enum class Transport(override val code: TrafficTerminationCode) : TrafficTerminationReason {
        UPSTREAM_CONNECTION_LIMIT(TrafficTerminationCode("upstream_connection_limit")),
        UPSTREAM_TLS_HANDSHAKE_FAILED(TrafficTerminationCode("upstream_tls_handshake_failed")),
        UPSTREAM_CONNECT_FAILED(TrafficTerminationCode("upstream_connect_failed")),
        UPSTREAM_REQUEST_WRITE_FAILED(TrafficTerminationCode("upstream_request_write_failed")),
        UPSTREAM_RESPONSE_FAILED(TrafficTerminationCode("upstream_response_failed")),
        DOWNSTREAM_CANCELLED(TrafficTerminationCode("downstream_cancelled")),
        DOWNSTREAM_CANCELLED_BEFORE_FORWARDING(TrafficTerminationCode("downstream_cancelled_before_forwarding")),
        DOWNSTREAM_CONNECTION_CLOSED(TrafficTerminationCode("downstream_connection_closed")),
        DOWNSTREAM_RESPONSE_REJECTED(TrafficTerminationCode("downstream_response_rejected")),
        DUPLEX_IO_FAILED(TrafficTerminationCode("duplex_io_failed")),
        DUPLEX_PEER_CLOSED(TrafficTerminationCode("duplex_peer_closed")),
        DUPLEX_WRITE_FAILED(TrafficTerminationCode("duplex_write_failed")),
        READ_TIMED_OUT(TrafficTerminationCode("read_timeout")),
        WRITE_TIMED_OUT(TrafficTerminationCode("write_timeout")),
    }

    /** Breakpoint or protocol transformation intentionally terminated forwarding. */
    public enum class Interception(override val code: TrafficTerminationCode) : TrafficTerminationReason {
        PROTOCOL_STREAM_TRANSFORM_FAILED(TrafficTerminationCode("protocol_stream_transform_failed")),
        DUPLEX_TRANSFORM_FAILED(TrafficTerminationCode("duplex_transform_failed")),
        BREAKPOINT_REQUEST_DROPPED(TrafficTerminationCode("breakpoint_request_dropped")),
        BREAKPOINT_ABANDONED(TrafficTerminationCode("breakpoint_abandoned")),
        INTERCEPTOR_REMOVED_BEFORE_FORWARDING(TrafficTerminationCode("interceptor_removed_before_forwarding")),
    }

    /** Compatibility reason for an old terminal record that did not persist a reason. */
    public enum class Unspecified(override val code: TrafficTerminationCode) : TrafficTerminationReason {
        FAILURE(TrafficTerminationCode("unspecified_failure")),
        CANCELLATION(TrafficTerminationCode("unspecified_cancellation")),
        DROP(TrafficTerminationCode("unspecified_drop")),
    }

    /** Extension-safe protocol-owned reason whose stable code is retained unchanged. */
    public data class Protocol(
        public val protocol: MessageProtocolId,
        override val code: TrafficTerminationCode,
    ) : TrafficTerminationReason

    /** Namespaced reason supplied by a future non-protocol extension. */
    public data class Extension(
        public val namespace: String,
        public val detail: TrafficTerminationCode,
    ) : TrafficTerminationReason {
        init {
            require(NAMESPACE_PATTERN.matches(namespace)) {
                "Traffic termination namespaces must use lowercase ASCII letters, numbers, or hyphens."
            }
        }

        override val code: TrafficTerminationCode = TrafficTerminationCode("$namespace.${detail.value}")
    }

    /** Forward-compatible value read from persistence but not understood by this KNet version. */
    public data class Unknown(
        override val code: TrafficTerminationCode,
    ) : TrafficTerminationReason

    public companion object {
        /** Decodes storage or wire data without discarding unknown future values. */
        public fun fromCode(value: String?): TrafficTerminationReason? {
            if (value == null) return null
            val code = TrafficTerminationCode(value)
            KNOWN_REASONS[value]?.let { return it }
            val protocol = when {
                value.startsWith("grpc_") -> MessageProtocolId.GRPC
                value.startsWith("websocket_") -> MessageProtocolId.WEBSOCKET
                value.startsWith("sse_") -> MessageProtocolId.SSE
                else -> null
            }
            return protocol?.let { Protocol(it, code) } ?: Unknown(code)
        }
    }
}

/** Terminal outcome that makes invalid exchange state/reason combinations unrepresentable. */
public sealed interface ExchangeTerminalOutcome {
    public val state: ExchangeState
    public val reason: TrafficTerminationReason?

    /** Exchange completed successfully. */
    public data object Completed : ExchangeTerminalOutcome {
        override val state: ExchangeState = ExchangeState.COMPLETED
        override val reason: TrafficTerminationReason? = null
    }

    /** Exchange failed because of a transport, protocol, interception, or runtime error. */
    public data class Failed(
        override val reason: TrafficTerminationReason,
    ) : ExchangeTerminalOutcome {
        override val state: ExchangeState = ExchangeState.FAILED
    }

    /** Exchange was intentionally dropped by a rule or protocol decision. */
    public data class Dropped(
        override val reason: TrafficTerminationReason,
    ) : ExchangeTerminalOutcome {
        override val state: ExchangeState = ExchangeState.DROPPED
    }

    /** Exchange was cancelled because its owner or peer ended before completion. */
    public data class Cancelled(
        override val reason: TrafficTerminationReason,
    ) : ExchangeTerminalOutcome {
        override val state: ExchangeState = ExchangeState.CANCELLED
    }

    public companion object {
        /** Reconstructs a typed outcome from durable state while accepting legacy missing reasons. */
        public fun fromPersisted(
            state: ExchangeState,
            reasonCode: String?,
        ): ExchangeTerminalOutcome? = when (state) {
            ExchangeState.COMPLETED -> Completed
            ExchangeState.FAILED -> Failed(
                TrafficTerminationReason.fromCode(reasonCode) ?: TrafficTerminationReason.Unspecified.FAILURE,
            )

            ExchangeState.DROPPED -> Dropped(
                TrafficTerminationReason.fromCode(reasonCode) ?: TrafficTerminationReason.Unspecified.DROP,
            )

            ExchangeState.CANCELLED -> Cancelled(
                TrafficTerminationReason.fromCode(reasonCode) ?: TrafficTerminationReason.Unspecified.CANCELLATION,
            )

            else -> null
        }
    }
}

/** Whether this lifecycle value is terminal and must never transition again. */
public val ExchangeState.isTerminal: Boolean
    get() = this == ExchangeState.COMPLETED || this == ExchangeState.FAILED ||
            this == ExchangeState.DROPPED || this == ExchangeState.CANCELLED

private val KNOWN_REASONS: Map<String, TrafficTerminationReason> = buildMap {
    TrafficTerminationReason.Lifecycle.entries.forEach { put(it.code.value, it) }
    TrafficTerminationReason.Transport.entries.forEach { put(it.code.value, it) }
    TrafficTerminationReason.Interception.entries.forEach { put(it.code.value, it) }
    TrafficTerminationReason.Unspecified.entries.forEach { put(it.code.value, it) }
}

private val NAMESPACE_PATTERN: Regex = Regex("[a-z0-9][a-z0-9-]{0,31}")
