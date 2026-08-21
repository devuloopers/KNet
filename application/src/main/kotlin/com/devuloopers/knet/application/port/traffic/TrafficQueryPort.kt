package com.devuloopers.knet.application.port.traffic

import com.devuloopers.knet.traffic.id.BodyId
import com.devuloopers.knet.traffic.id.CaptureSessionId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.HttpExchangeSnapshot
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.HttpStatus
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import kotlinx.coroutines.flow.Flow

/**
 * Opaque keyset cursor returned by the traffic store.
 *
 * @property value Non-blank cursor token that callers must not parse.
 */
@JvmInline
public value class TrafficPageCursor(public val value: String) {
    init {
        require(value.isNotBlank()) { "Traffic page cursor must not be blank." }
    }
}

/**
 * Sort direction for an indexed traffic query.
 */
public enum class TrafficSortDirection {
    NEWEST_FIRST,
    OLDEST_FIRST,
}

/**
 * Indexed query requested by Traffic or another authorized feature.
 *
 * @property sessionId Optional capture session to query, or null for retained history across sessions.
 * @property cursor Optional keyset cursor from a previous page.
 * @property limit Maximum number of records to return.
 * @property direction Timestamp/keyset sort direction.
 * @property searchContains Optional host, path, method, or status text filter executed by the store.
 * @property methods Optional typed method filter.
 * @property statuses Optional typed status filter.
 * @property schemes Optional typed request-scheme filter.
 * @property protocols Optional typed effective application-protocol filter.
 */
public data class TrafficPageQuery(
    public val sessionId: CaptureSessionId? = null,
    public val cursor: TrafficPageCursor? = null,
    public val limit: Int,
    public val direction: TrafficSortDirection = TrafficSortDirection.NEWEST_FIRST,
    public val searchContains: String? = null,
    public val methods: Set<HttpMethod> = emptySet(),
    public val statuses: Set<HttpStatus> = emptySet(),
    public val schemes: Set<HttpScheme> = emptySet(),
    public val protocols: Set<ApplicationProtocol> = emptySet(),
) {
    init {
        require(limit in 1..1_000) { "Traffic page limit must be between 1 and 1000." }
    }
}

/**
 * Durable one-based capture order assigned by canonical storage.
 *
 * The value belongs to an exchange rather than to a loaded UI window, so loading another page
 * cannot renumber rows that are already visible.
 *
 * @property value Positive storage-owned capture sequence.
 */
@JvmInline
public value class TrafficCaptureSequence(public val value: Long) {
    init {
        require(value > 0L) { "Traffic capture sequence must be positive." }
    }
}

/**
 * One canonical page item with its stable storage ordering metadata.
 *
 * @property captureSequence Durable capture order used for paging and presentation identity.
 * @property exchange Shared canonical request/response snapshot used by Traffic, API Studio,
 * breakpoints, and protocol inspectors.
 */
public data class TrafficPageItem(
    public val captureSequence: TrafficCaptureSequence,
    public val exchange: HttpExchangeSnapshot,
)

/**
 * One bounded page of canonical exchange snapshots and storage-owned paging metadata.
 *
 * @property items Returned canonical page items.
 * @property nextCursor Cursor for the following page, or null at the end.
 * @property totalCount Exact number of records matching the query at first-page evaluation.
 * @property generation Store generation used to detect a stale live page.
 */
public data class TrafficPage(
    public val items: List<TrafficPageItem>,
    public val nextCursor: TrafficPageCursor?,
    public val totalCount: Long,
    public val generation: Long,
) {
    init {
        require(totalCount >= 0L) { "Traffic page total count must not be negative." }
        require(generation >= 0L) { "Traffic page generation must not be negative." }
    }
}

/**
 * Bounded body range requested by an authorized feature.
 *
 * @property offset Zero-based byte offset.
 * @property length Maximum bytes to return.
 */
public data class BodyRange(
    public val offset: Long,
    public val length: Int,
) {
    init {
        require(offset >= 0L) { "Body range offset must not be negative." }
        require(length in 1..1_048_576) { "Body range length must be between 1 and 1048576 bytes." }
    }
}

/**
 * Immutable-copy body range returned to an application feature.
 *
 * The constructor and [copyBytes] defensively copy content so callers cannot mutate a shared
 * storage or cache buffer.
 *
 * @param bytes Bounded body bytes returned by the storage adapter.
 * @property offset Source body offset of the first byte.
 * @property endOfBody Whether the returned range reaches the available stored body end.
 */
public class BodyChunk(
    bytes: ByteArray,
    public val offset: Long,
    public val endOfBody: Boolean,
) {
    private val content: ByteArray = bytes.copyOf()

    init {
        require(offset >= 0L) { "Body chunk offset must not be negative." }
        require(content.size <= 1_048_576) { "Body chunk exceeds the application range limit." }
    }

    /** Number of bytes in this bounded chunk. */
    public val size: Int
        get() = content.size

    /**
     * Returns a defensive copy of the chunk content.
     *
     * @return Independent byte array owned by the caller.
     */
    public fun copyBytes(): ByteArray = content.copyOf()

    public override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BodyChunk) return false
        return offset == other.offset && endOfBody == other.endOfBody && content.contentEquals(other.content)
    }

    public override fun hashCode(): Int {
        var result = content.contentHashCode()
        result = 31 * result + offset.hashCode()
        result = 31 * result + endOfBody.hashCode()
        return result
    }

    public override fun toString(): String = "BodyChunk(size=$size, offset=$offset, endOfBody=$endOfBody)"
}

/**
 * Compact signal that paged traffic results may have changed.
 *
 * @property sessionId Changed capture session.
 * @property generation Monotonically increasing store generation.
 */
public data class TrafficGeneration(
    public val sessionId: CaptureSessionId,
    public val generation: Long,
) {
    init {
        require(generation >= 0L) { "Traffic generation must not be negative." }
    }
}

/**
 * Application boundary for selecting the most recently displayable capture session.
 *
 * Session discovery is intentionally separate from [TrafficQueryPort]: presentations observe one
 * compact identifier and then issue bounded page queries. Implementations may select a current
 * canonical session without exposing storage schema details.
 */
public interface TrafficSessionCatalogPort {
    /** Latest session available to Traffic, or null when no session can be displayed. */
    public val latestSessionId: Flow<CaptureSessionId?>
}

/** Application port for bounded access to body content owned by a storage adapter. */
public interface BodyAccessPort {
    /**
     * Reads one bounded range from body storage.
     *
     * @param bodyId Opaque body identifier.
     * @param range Bounded range request.
     * @return Immutable-copy body chunk.
     * @throws IllegalStateException When the body is missing or unavailable.
     */
    public suspend fun readBody(bodyId: BodyId, range: BodyRange): BodyChunk
}

/**
 * Application port for paged traffic metadata and bounded body access.
 *
 * Implementations query indexed storage and never expose Room entities, filesystem paths,
 * complete unbounded session lists, or unrestricted body reads.
 */
public interface TrafficQueryPort : BodyAccessPort {
    /** Compact change generations observed by live traffic presentations. */
    public val generations: Flow<TrafficGeneration>

    /**
     * Executes one indexed keyset query.
     *
     * @param query Bounded query definition.
     * @return Bounded page of canonical snapshots.
     */
    public suspend fun query(query: TrafficPageQuery): TrafficPage

    /**
     * Loads one exchange directly by stable identifier.
     *
     * @param exchangeId Exchange identifier.
     * @return Snapshot or null when absent/removed.
     */
    public suspend fun getExchange(exchangeId: ExchangeId): HttpExchangeSnapshot?
}
