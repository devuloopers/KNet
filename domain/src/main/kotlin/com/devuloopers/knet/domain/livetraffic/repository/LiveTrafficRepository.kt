package com.devuloopers.knet.domain.livetraffic.repository

import com.devuloopers.knet.model.HttpTransaction
import kotlinx.coroutines.flow.Flow

/**
 * Feature repository contract for the Live Traffic feed.
 * Isolates data fetching, session clearing, and proxy flow operations for live traffic.
 */
interface LiveTrafficRepository {

    /**
     * Cold stream returning the chronologically descending HTTP transaction list from Room DB.
     */
    val transactionsFlow: Flow<List<HttpTransaction>>

    /**
     * Clears all recorded transaction records and payload storage.
     */
    fun clearSession()
}
