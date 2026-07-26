package com.devuloopers.knet.domain.inspector.usecase

import com.devuloopers.knet.domain.inspector.model.InspectorTab
import com.devuloopers.knet.domain.inspector.model.InspectorUiState
import com.devuloopers.knet.domain.inspector.model.TransactionUiModel
import com.devuloopers.knet.domain.inspector.repository.InspectorRepository
import com.devuloopers.knet.domain.livetraffic.model.UriDetails
import com.devuloopers.knet.model.HttpTransaction
import com.devuloopers.knet.domain.utils.decodeBodyToText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Domain UseCase that fetches, decodes, and formats selected transaction entities off-thread.
 *
 * @property repository Feature repository contract for inspection details.
 */
class GetTransactionDetailUseCase(
    private val repository: InspectorRepository
) {
    /**
     * Executes transaction details query and formats into [InspectorUiState].
     *
     * @param transactionId Target transaction unique ID.
     * @param activeTab Currently active tab.
     * @return Flow emitting formatted [InspectorUiState].
     */
    fun execute(transactionId: String?, activeTab: InspectorTab = InspectorTab.OVERVIEW): Flow<InspectorUiState> {
        if (transactionId.isNullOrEmpty()) {
            return flowOf(InspectorUiState.NoSelection)
        }

        return repository.getTransactionById(transactionId).map { tx ->
            if (tx == null) {
                InspectorUiState.NoSelection
            } else {
                val uiModel = mapToUiModel(tx)
                InspectorUiState.Success(transaction = uiModel, activeTab = activeTab)
            }
        }.flowOn(Dispatchers.Default)
    }

    private fun mapToUiModel(tx: HttpTransaction): TransactionUiModel {
        val uriDetails = UriDetails.parse(tx.request.url)
        val durationMs = tx.durationMs
        val reqLen = tx.request.body?.size ?: 0
        val resLen = tx.response?.body?.size ?: 0
        val totalBytes = reqLen + resLen

        val sizeText = if (tx.response == null) {
            "-"
        } else {
            when {
                totalBytes >= 1024 * 1024 -> "%.2f MB".format(totalBytes / (1024.0 * 1024.0))
                totalBytes >= 1024 -> "%.2f KB".format(totalBytes / 1024.0)
                else -> "$totalBytes B"
            }
        }

        val requestBodyText = decodeBodyToText(tx.request.body, tx.request.headers)
        val responseBodyText = if (tx.response?.body != null) {
            decodeBodyToText(tx.response.body, tx.response.headers)
        } else ""

        return TransactionUiModel(
            id = 1,
            method = tx.request.method,
            host = uriDetails.host,
            path = uriDetails.path,
            status = tx.response?.statusCode ?: 0,
            statusText = tx.response?.statusText ?: "Active",
            time = if (tx.response == null) "-" else "$durationMs ms",
            size = sizeText,
            dateGroup = "Today",
            requestBody = requestBodyText,
            responseBody = responseBodyText,
            queryParams = uriDetails.queryParams,
            requestHeaders = tx.request.headers.toMap(),
            responseHeaders = tx.response?.headers?.toMap() ?: emptyMap(),
            timings = tx.timings
        )
    }
}
