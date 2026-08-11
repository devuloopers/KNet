package com.devuloopers.knet.domain.traffic.usecase

import com.devuloopers.knet.domain.clientNetwork.model.HttpTransaction
import com.devuloopers.knet.domain.traffic.model.LiveTrafficUiState
import com.devuloopers.knet.domain.traffic.model.ProtocolFilter
import com.devuloopers.knet.domain.traffic.model.TrafficItemUiState
import com.devuloopers.knet.domain.traffic.model.UriDetails
import com.devuloopers.knet.domain.traffic.repository.LiveTrafficRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.net.URI

/**
 * Domain UseCase that filters, formats, and transforms raw domain [HttpTransaction] streams
 * into immutable [LiveTrafficUiState] models off the main thread.
 *
 * Body payloads are intentionally omitted from the UI state produced by this use case.
 * Body bytes are disk files and must only be read on-demand via [LoadTransactionBodyUseCase]
 * when the user selects a row in the inspector.
 *
 * @property repository The live traffic repository contract.
 */
class GetLiveTrafficUseCase(
    private val repository: LiveTrafficRepository
) {

    /**
     * Executes filtering and mapping on the repository transaction stream.
     *
     * @param filter Target protocol chip filter.
     * @param searchQuery Filter query string.
     * @param selectedId Id of currently selected transaction, or null.
     * @return Cold Flow emitting formatted [LiveTrafficUiState].
     */
    fun execute(
        filter: ProtocolFilter,
        searchQuery: String,
        selectedId: String? = null
    ): Flow<LiveTrafficUiState> {
        return repository.transactionsFlow.map { transactions ->
            if (transactions.isEmpty()) {
                LiveTrafficUiState.Empty(filter, searchQuery)
            } else {
                val filtered = transactions.filterIndexed { _, tx ->
                    matchesFilter(tx, filter) && matchesSearch(tx, searchQuery)
                }

                if (filtered.isEmpty()) {
                    LiveTrafficUiState.Empty(filter, searchQuery)
                } else {
                    val totalCount = filtered.size
                    // List is chronologically descending (index 0 = newest transaction).
                    // Newest transaction gets sequentialId = totalCount (#N at top), oldest gets #1.
                    val displayedItems = filtered.mapIndexed { index, tx ->
                        val sequentialId = totalCount - index
                        mapToUiState(sequentialId, tx, selectedId)
                    }
                    val selectedItem = displayedItems.find { it.transactionId == selectedId }
                    LiveTrafficUiState.Success(
                        items = displayedItems,
                        totalCount = displayedItems.size,
                        activeFilter = filter,
                        searchQuery = searchQuery,
                        selectedItem = selectedItem
                    )
                }
            }
        }.flowOn(Dispatchers.Default)
    }

    private fun matchesFilter(tx: HttpTransaction, filter: ProtocolFilter): Boolean {
        val scheme = try {
            URI(tx.request.url).scheme ?: "https"
        } catch (_: Exception) {
            "https"
        }
        val reqHeaders = tx.request.headers.toMap()
        val resHeaders = tx.response?.headers?.toMap() ?: emptyMap()

        return when (filter) {
            ProtocolFilter.ALL -> true
            ProtocolFilter.HTTP -> scheme.lowercase() == "http"
            ProtocolFilter.HTTPS -> scheme.lowercase() == "https"
            ProtocolFilter.WEBSOCKET -> scheme.contains("ws") ||
                    reqHeaders["upgrade"]?.contains("websocket", ignoreCase = true) == true ||
                    resHeaders["upgrade"]?.contains("websocket", ignoreCase = true) == true

            ProtocolFilter.HTTP_2 -> tx.request.protocol.contains("2")
            ProtocolFilter.GRPC -> reqHeaders["content-type"]?.contains("grpc", ignoreCase = true) == true ||
                    resHeaders["content-type"]?.contains("grpc", ignoreCase = true) == true ||
                    tx.request.url.contains("grpc", ignoreCase = true)
            ProtocolFilter.OTHER -> scheme.lowercase() != "http" && scheme.lowercase() != "https" &&
                    !scheme.contains("ws") && !tx.request.protocol.contains("2")
        }
    }

    private fun matchesSearch(tx: HttpTransaction, searchQuery: String): Boolean {
        if (searchQuery.isBlank()) return true
        val query = searchQuery.trim().lowercase()
        val url = tx.request.url.lowercase()
        val method = tx.request.method.lowercase()
        val status = tx.response?.statusCode?.toString() ?: ""
        return url.contains(query) || method.contains(query) || status.contains(query)
    }

    private fun mapToUiState(sequentialId: Int, tx: HttpTransaction, selectedId: String?): TrafficItemUiState {
        val uriDetails = UriDetails.parse(tx.request.url)
        val durationMs = tx.durationMs
        val sizeText = if (tx.response == null) {
            "-"
        } else {
            // Sizes are stored at capture time in dedicated DB columns — never computed from body bytes.
            val totalBytes = tx.requestBodySize + tx.responseBodySize
            when {
                totalBytes >= 1024 * 1024 -> "%.2f MB".format(totalBytes / (1024.0 * 1024.0))
                totalBytes >= 1024 -> "%.2f KB".format(totalBytes / 1024.0)
                totalBytes > 0L -> "$totalBytes B"
                else -> "0 B"
            }
        }

        val formattedTimestamp = formatTimestamp(tx.request.timestamp)

        // Body bytes are intentionally omitted here — disk I/O is deferred to LoadTransactionBodyUseCase.
        // The inspector panel calls that use case when the user selects this row.
        return TrafficItemUiState(
            id = sequentialId,
            transactionId = tx.id,
            method = tx.request.method,
            host = uriDetails.host,
            path = uriDetails.path,
            status = tx.response?.statusCode ?: 0,
            statusText = tx.response?.statusText ?: "Active",
            timestamp = tx.request.timestamp,
            formattedTimestamp = formattedTimestamp,
            formattedTime = if (tx.response == null) "-" else "$durationMs ms",
            formattedSize = sizeText,
            dateGroup = "Today",
            requestBody = "",
            responseBody = "",
            queryParams = uriDetails.queryParams,
            requestHeaders = tx.request.headers.toMap(),
            responseHeaders = tx.response?.headers?.toMap() ?: emptyMap(),
            timings = tx.timings,
            isSelected = tx.id == selectedId
        )
    }

    private fun formatTimestamp(epochMillis: Long): String {
        if (epochMillis <= 0L) return "-"
        return try {
            val txInstant = java.time.Instant.ofEpochMilli(epochMillis)
            val txDateTime = java.time.LocalDateTime.ofInstant(txInstant, java.time.ZoneId.systemDefault())
            val todayDate = java.time.LocalDate.now(java.time.ZoneId.systemDefault())

            if (txDateTime.toLocalDate() == todayDate) {
                txDateTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
            } else {
                txDateTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss - dd/MM"))
            }
        } catch (_: Throwable) {
            "-"
        }
    }
}
