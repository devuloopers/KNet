package com.devuloopers.knet.domain.inspector.repository

import com.devuloopers.knet.model.HttpTransaction
import kotlinx.coroutines.flow.Flow

/**
 * Feature repository contract for the Inspector panel.
 */
interface InspectorRepository {
    /**
     * Cold stream returning the selected transaction entity by ID.
     */
    fun getTransactionById(transactionId: String): Flow<HttpTransaction?>
}
