package com.devuloopers.knet.ui.desktop.traffic.model

import com.devuloopers.knet.domain.proxy.model.ProxyEngineState
import com.devuloopers.knet.domain.traffic.model.TrafficItemUiState

/**
 * Capture execution state enum.
 */
public enum class CaptureState {
    IDLE,
    CAPTURING,
    PAUSED,
    STOPPED
}

/**
 * Inspection panel sub-tabs enum.
 */
public enum class InspectorTab {
    OVERVIEW,
    REQUEST,
    RESPONSE,
    TIMELINE
}

/**
 * Preview format switcher mode enum.
 */
public enum class PreviewFormatMode {
    PRETTY,
    RAW,
    HEX
}

/**
 * Top-level immutable UI state for `:ui:desktop:traffic` workspace.
 *
 * @property transactions Full live list of captured transactions.
 * @property filteredTransactions Filtered subset of transactions based on query & protocol/method/status filters.
 * @property selectedTransactionId Currently selected transaction ID, or null.
 * @property captureState Current proxy capture lifecycle state.
 * @property engineState Current proxy engine state (Stopped, Starting, Running, Stopping, Error).
 * @property searchQuery Live search filter query text.
 * @property selectedProtocolFilter Active protocol filter chip (e.g., "ALL", "HTTP", "HTTPS", "WS", "OTHER").
 * @property selectedMethodFilter Active HTTP method dropdown filter (e.g., "ALL", "GET", "POST").
 * @property selectedStatusFilter Active HTTP status dropdown filter (e.g., "ALL", "2XX", "3XX", "4XX", "5XX").
 * @property autoScroll Whether auto-scrolling to newest transaction is enabled.
 * @property activeInspectorTab Currently selected inspection tab.
 * @property activeRequestSubTab Currently selected request sub-tab (Headers, Query, Body).
 * @property activeResponseSubTab Currently selected response sub-tab (Headers, Body).
 * @property previewFormatMode Active response/request body preview format mode.
 */
public data class TrafficState(
    val transactions: List<TrafficItemUiState> = emptyList(),
    val filteredTransactions: List<TrafficItemUiState> = emptyList(),
    val selectedTransactionId: String? = null,
    val captureState: CaptureState = CaptureState.STOPPED,
    val engineState: ProxyEngineState = ProxyEngineState.Stopped,
    val searchQuery: String = "",
    val selectedProtocolFilter: String = "ALL",
    val selectedMethodFilter: String = "ALL",
    val selectedStatusFilter: String = "ALL",
    val autoScroll: Boolean = true,
    val activeInspectorTab: InspectorTab = InspectorTab.OVERVIEW,
    val activeRequestSubTab: RequestSubTab = RequestSubTab.HEADERS,
    val activeResponseSubTab: ResponseSubTab = ResponseSubTab.BODY,
    val previewFormatMode: PreviewFormatMode = PreviewFormatMode.PRETTY,
    val columnVisibility: ColumnVisibilityState = ColumnVisibilityState(),
    val preparedState: InspectorPreparedState = InspectorPreparedState()
) {
    /**
     * Selected transaction UI model matching [selectedTransactionId].
     */
    public val selectedTransaction: TrafficItemUiState?
        get() = transactions.find { it.transactionId == selectedTransactionId }
            ?: filteredTransactions.find { it.transactionId == selectedTransactionId }
            ?: transactions.firstOrNull()

    /**
     * Calculated statistics counts for quick protocol chips.
     */
    public val httpCount: Int
        get() = transactions.count { it.protocol.startsWith("HTTP/1") || (it.protocol.startsWith("HTTP") && !it.protocol.startsWith("HTTP/2")) }

    public val httpsCount: Int
        get() = transactions.count { it.protocol.startsWith("HTTP/2") || it.protocol.equals("HTTPS", ignoreCase = true) }

    public val wsCount: Int
        get() = transactions.count { it.method.equals("WS", ignoreCase = true) || it.protocol.equals("WS", ignoreCase = true) }

    public val otherCount: Int
        get() = (transactions.size - (httpCount + httpsCount + wsCount)).coerceAtLeast(0)

    /**
     * Calculated total transferred payload size string.
     */
    public val formattedTotalSize: String
        get() {
            val totalBytes = transactions.sumOf { tx ->
                tx.formattedSize.replace(" KB", "").replace(" B", "").replace(" MB", "").toDoubleOrNull()?.let { size ->
                    when {
                        tx.formattedSize.contains("MB") -> (size * 1024 * 1024).toLong()
                        tx.formattedSize.contains("KB") -> (size * 1024).toLong()
                        else -> size.toLong()
                    }
                } ?: 0L
            }
            return when {
                totalBytes >= 1024 * 1024 -> "${(totalBytes / (1024.0 * 1024.0) * 100).toLong() / 100.0} MB"
                totalBytes >= 1024 -> "${(totalBytes / 1024.0 * 100).toLong() / 100.0} KB"
                else -> "$totalBytes B"
            }
        }
}
