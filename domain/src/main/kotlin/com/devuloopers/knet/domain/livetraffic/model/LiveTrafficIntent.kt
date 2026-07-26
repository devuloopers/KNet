package com.devuloopers.knet.domain.livetraffic.model

/**
 * Sealed interface representing Unidirectional Data Flow (UDF) user actions for Live Traffic.
 */
sealed interface LiveTrafficIntent {

    /** Emitted when a user clicks a protocol filter chip (All, HTTP, HTTPS, etc.). */
    data class SelectProtocol(val filter: ProtocolFilter) : LiveTrafficIntent

    /** Emitted when a user types in the search query input bar. */
    data class SearchQueryChanged(val query: String) : LiveTrafficIntent

    /** Emitted when a user clicks a specific transaction row to select it. */
    data class SelectTransaction(val transactionId: String) : LiveTrafficIntent

    /** Emitted when a user triggers session clear. */
    object ClearTraffic : LiveTrafficIntent
}
