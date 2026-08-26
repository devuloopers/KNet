package com.devuloopers.knet.application.contract.traffic

import com.devuloopers.knet.traffic.id.CaptureSessionId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.HttpExchangeSnapshot
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.HttpStatus
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
 * Session discovery is intentionally separate from [TrafficQuery]: presentations observe one
 * compact identifier and then issue bounded page queries. Implementations may select a current
 * canonical session without exposing storage schema details.
 */
public interface TrafficSessionCatalog {
    /** Latest session available to Traffic, or null when no session can be displayed. */
    public val latestSessionId: Flow<CaptureSessionId?>
}

/**
 * Application contract for paged traffic metadata and bounded body access.
 *
 * Implementations query indexed storage and never expose Room entities, filesystem paths,
 * complete unbounded session lists, or unrestricted body reads.
 */
public interface TrafficQuery : BodyAccess {
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
