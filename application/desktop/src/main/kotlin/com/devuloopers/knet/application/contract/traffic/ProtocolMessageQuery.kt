package com.devuloopers.knet.application.contract.traffic

import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.message.ProtocolMessageSnapshot
import kotlinx.coroutines.flow.Flow

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
}
