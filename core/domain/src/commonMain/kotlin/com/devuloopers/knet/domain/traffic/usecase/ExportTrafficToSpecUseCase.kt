package com.devuloopers.knet.domain.traffic.usecase

import com.devuloopers.knet.domain.collection.model.HttpMethod
import com.devuloopers.knet.domain.network.mapper.NetworkSpecMappers.toNetworkRequestSpec
import com.devuloopers.knet.domain.network.model.NetworkRequestSpec
import com.devuloopers.knet.domain.traffic.model.TrafficItemUiState
import com.devuloopers.knet.domain.traffic.repository.LiveTrafficRepository
import com.devuloopers.knet.domain.util.decodeBodyToText

/**
 * Domain UseCase that converts a captured traffic transaction into a zero-data-loss [NetworkRequestSpec]
 * for export to API Studio or external request builders off the main thread.
 *
 * Encapsulates transaction retrieval, lazy body loading from disk, payload decoding, and domain specification mapping.
 * Supports primary SQLite database lookup with in-memory UI state fallback.
 *
 * @property repository Live traffic repository supplying transaction streams and body loading.
 */
class ExportTrafficToSpecUseCase(
    private val repository: LiveTrafficRepository
) {

    /**
     * Executes export mapping for the specified transaction ID.
     *
     * @param transactionId Unique UUID of the target transaction.
     * @param cachedReqBody Optional pre-loaded request body text string from memory.
     * @param fallbackItem Optional UI state item from memory if database record is missing or purged.
     * @return Formatted [NetworkRequestSpec], or null if the transaction cannot be resolved.
     */
    suspend fun execute(
        transactionId: String,
        cachedReqBody: String? = null,
        fallbackItem: TrafficItemUiState? = null
    ): NetworkRequestSpec? {
        val reqBodyText = if (!cachedReqBody.isNullOrBlank()) {
            cachedReqBody
        } else {
            val bodyPayload = repository.loadTransactionBody(transactionId)
            decodeBodyToText(bodyPayload.requestBody, bodyPayload.requestHeaders)
        }

        // 1. Primary Strategy: Try direct SQLite DB entity lookup
        val httpTx = repository.getTransactionById(transactionId)
        if (httpTx != null) {
            return httpTx.toNetworkRequestSpec(reqBodyText)
        }

        // 2. Fallback Strategy: Map directly from displayed table item if DB entity is absent
        if (fallbackItem != null) {
            val headerPairs = fallbackItem.requestHeaders.map { it.key to it.value }
            val queryParamsList = fallbackItem.queryParams.map { it.key to it.value.toString() }
            val parsedMethod = try {
                HttpMethod.valueOf(fallbackItem.method.uppercase())
            } catch (_: Exception) {
                HttpMethod.CUSTOM
            }
            val targetUrl = if (fallbackItem.host.isNotBlank()) "https://${fallbackItem.host}${fallbackItem.path}" else fallbackItem.path

            return NetworkRequestSpec(
                method = parsedMethod,
                customMethod = if (parsedMethod == HttpMethod.CUSTOM) fallbackItem.method else null,
                url = targetUrl,
                headers = headerPairs,
                queryParams = queryParamsList,
                bodyPayload = reqBodyText,
                timestamp = fallbackItem.timestamp
            )
        }

        return null
    }
}
