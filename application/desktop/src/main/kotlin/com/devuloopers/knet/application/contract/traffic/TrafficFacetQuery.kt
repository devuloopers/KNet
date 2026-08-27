package com.devuloopers.knet.application.contract.traffic

import com.devuloopers.knet.traffic.id.CaptureSessionId
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.HttpStatus
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol

/**
 * Filters shared by the Traffic facet counts.
 *
 * The scheme selection is intentionally absent. Facet counts describe the scheme choices
 * available after applying search, method, status, and HTTP-version filters, so selecting an
 * empty scheme never erases the other scheme choices.
 */
public data class TrafficFacetQuery(
    public val sessionId: CaptureSessionId? = null,
    public val searchContains: String? = null,
    public val methods: Set<HttpMethod> = emptySet(),
    public val statuses: Set<HttpStatus> = emptySet(),
    public val protocols: Set<ApplicationProtocol> = emptySet(),
)

/** Exact aggregate counts for the Traffic request-scheme facet. */
public data class TrafficFacetCounts(
    public val totalCount: Long = 0L,
    public val httpCount: Long = 0L,
    public val httpsCount: Long = 0L,
) {
    init {
        require(
            listOf(
                totalCount,
                httpCount,
                httpsCount,
            ).all { count -> count >= 0L },
        ) { "Traffic facet counts must not be negative." }
    }
}

/** Indexed aggregate reader used by filter presentations without loading exchange rows. */
public fun interface TrafficFacetReader {
    public suspend fun queryFacets(query: TrafficFacetQuery): TrafficFacetCounts
}
