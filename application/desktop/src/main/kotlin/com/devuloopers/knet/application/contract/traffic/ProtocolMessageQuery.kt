package com.devuloopers.knet.application.contract.traffic

import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.message.ProtocolMessageSnapshot
import kotlinx.coroutines.flow.Flow
import com.devuloopers.knet.traffic.id.ProtocolMessageId
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.message.MessageProtocolId
import com.devuloopers.knet.traffic.model.message.ProtocolMessageKind

/** Bounded context before one captured message, ordered newest first by durable capture sequence. */
public data class ProtocolMessageContextQuery(
    public val exchangeId: ExchangeId,
    public val beforeMessageId: ProtocolMessageId,
    public val protocol: MessageProtocolId,
    public val direction: TrafficDirection?,
    public val kind: ProtocolMessageKind,
    public val limit: Int = MAXIMUM_MESSAGES,
) {
    init { require(limit in 1..MAXIMUM_MESSAGES) { "Protocol context exceeds its message bound." } }
    public companion object {
        /** Limits both metadata lookup and subsequent retained context payloads. */
        public const val MAXIMUM_MESSAGES: Int = 128
    }
}

/** One owned bounded context body; absent/truncated payloads are explicit evidence gaps, never guessed operations. */
public class ProtocolMessageContextEntry(
    public val message: ProtocolMessageSnapshot,
    payload: ByteArray?,
    public val truncated: Boolean,
) {
    init { require(payload == null || payload.size <= MAXIMUM_PAYLOAD_BYTES) { "Context payload exceeds its bound." } }
    private val bytes = payload?.copyOf()
    /** Retained byte weight, exposed without allocating a second copy for aggregate budget checks. */
    public val retainedBytes: Int get() = bytes?.size ?: 0
    /** Returns a defensive copy for the protocol-owned parser. */
    public fun payload(): ByteArray? = bytes?.copyOf()
    override fun equals(other: Any?): Boolean = other is ProtocolMessageContextEntry && message == other.message &&
        truncated == other.truncated && (if (bytes == null) other.bytes == null else other.bytes != null && bytes.contentEquals(other.bytes))
    override fun hashCode(): Int = 31 * (31 * message.hashCode() + (bytes?.contentHashCode() ?: 0)) + truncated.hashCode()
    public companion object {
        /** Context is optional: large subscription documents remain unnamed rather than allocating unbounded history. */
        public const val MAXIMUM_PAYLOAD_BYTES: Int = 65_536
    }
}

/** Opaque keyset cursor for framed child-message pages. */
@JvmInline
public value class ProtocolMessagePageCursor(public val value: String) {
    init {
        require(value.isNotBlank()) { "Protocol message cursor must not be blank." }
    }
}

/** Bounded query for the framed messages owned by one canonical exchange. */
public data class ProtocolMessagePageQuery(
    public val exchangeId: ExchangeId,
    public val cursor: ProtocolMessagePageCursor? = null,
    public val limit: Int = 100,
) {
    init {
        require(limit in 1..1_000) { "Protocol message page limit must be between 1 and 1000." }
    }
}

/** One bounded child-message page with an exact stable first-page count. */
public data class ProtocolMessagePage(
    public val items: List<ProtocolMessageSnapshot>,
    public val nextCursor: ProtocolMessagePageCursor?,
    public val totalCount: Long,
) {
    init {
        require(totalCount >= 0L) { "Protocol message total count must not be negative." }
    }
}

/**
 * Application boundary for framed child messages such as gRPC and future WebSocket frames.
 *
 * Payload bytes continue through [BodyAccess]; this contract exposes bounded metadata pages only.
 */
public interface ProtocolMessageQuery : BodyAccess {
    /** Compact invalidation stream for the selected exchange. */
    public fun observeChanges(exchangeId: ExchangeId): Flow<Long>

    /** Executes one indexed keyset query. */
    public suspend fun queryMessages(query: ProtocolMessagePageQuery): ProtocolMessagePage

    /** Reads only earlier scoped metadata; providers without context support safely omit optional operation names. */
    public suspend fun queryContext(query: ProtocolMessageContextQuery): List<ProtocolMessageSnapshot> = emptyList()
}
