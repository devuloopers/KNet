package com.devuloopers.knet.ui.desktop.traffic.model

import com.devuloopers.knet.application.port.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.application.port.proxy.ProxyRuntimeState
import com.devuloopers.knet.application.port.traffic.TrafficPageCursor
import com.devuloopers.knet.traffic.id.CaptureSessionId
import com.devuloopers.knet.domain.rules.model.BreakpointRule
import com.devuloopers.knet.domain.workspace.model.TrafficTableColumnWidths
import com.devuloopers.knet.ui.desktop.httppanel.model.InspectorSubTab
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.StandardApplicationProtocol
import com.devuloopers.knet.traffic.model.http.StandardHttpScheme

/**
 * Capture execution state enum.
 */
enum class CaptureState {
    STARTING,
    CAPTURING,
    PAUSED,
    STOPPED
}

/**
 * Inspection panel sub-tabs enum.
 */
enum class InspectorTab {
    OVERVIEW,
    REQUEST,
    RESPONSE,
    TIMELINE
}

/**
 * Top-level immutable UI state for `:ui:desktop:traffic` workspace.
 *
 * @property transactions Full live list of captured transactions.
 * @property filteredTransactions Filtered subset of transactions based on query & protocol/method/status filters.
 * @property totalAvailableCount Exact storage count for the active paged query.
 * @property selectedTransactionId Currently selected transaction ID, or null.
 * @property captureState Current proxy capture lifecycle state.
 * @property engineState Current application-owned proxy runtime state.
 * @property searchQuery Live search filter query text.
 * @property selectedProtocolFilter Active protocol filter chip (e.g., [ProtocolFilter.ALL], [ProtocolFilter.HTTP], [ProtocolFilter.HTTPS]).
 * @property selectedMethodFilter Active HTTP method dropdown filter (e.g., [MethodFilter.ALL], [MethodFilter.GET], [MethodFilter.POST]).
 * @property selectedStatusFilter Active HTTP status dropdown filter (e.g., [StatusFilter.ALL], [StatusFilter.STATUS_2XX], [StatusFilter.STATUS_3XX]).
 * @property autoScroll Whether auto-scrolling to newest transaction is enabled.
 * @property activeInspectorTab Currently selected inspection tab.
 * @property activeRequestSubTab Currently selected request sub-tab (Headers, Query, Body).
 * @property activeResponseSubTab Currently selected response sub-tab (Headers, Body).
 * @property prefilledBreakpointProtocolValues Generic extension-owned values for a captured rule draft.
 * @property columnWidths Shared persisted widths used by both Traffic table headers and rows.
 */
data class TrafficState(
    val transactions: List<TrafficRowUiState> = emptyList(),
    val filteredTransactions: List<TrafficRowUiState> = emptyList(),
    val totalAvailableCount: Long = 0L,
    val sessionId: CaptureSessionId? = null,
    val nextPageCursor: TrafficPageCursor? = null,
    val pageGeneration: Long = 0L,
    val isPageLoading: Boolean = false,
    val isClearingHistory: Boolean = false,
    val selectedTransactionId: String? = null,
    val captureState: CaptureState = CaptureState.STOPPED,
    val engineState: ProxyRuntimeState = ProxyRuntimeState.Stopped,
    val engineErrorMessage: String? = null,
    val trafficErrorMessage: String? = null,
    val searchQuery: String = "",
    val selectedProtocolFilter: ProtocolFilter = ProtocolFilter.ALL,
    val selectedMethodFilter: MethodFilter = MethodFilter.ALL,
    val selectedStatusFilter: StatusFilter = StatusFilter.ALL,
    val autoScroll: Boolean = true,
    val activeInspectorTab: InspectorTab = InspectorTab.OVERVIEW,
    val activeRequestSubTab: InspectorSubTab = InspectorSubTab.BODY,
    val activeResponseSubTab: InspectorSubTab = InspectorSubTab.BODY,
    val columnVisibility: ColumnVisibilityState = ColumnVisibilityState(),
    val columnWidths: TrafficTableColumnWidths = TrafficTableColumnWidths(),
    val preparedState: InspectorPreparedState = InspectorPreparedState(),
    val localIpAddress: String = "127.0.0.1",
    val activeBreakpointRules: List<BreakpointRule> = emptyList(),
    val isBreakpointDialogVisible: Boolean = false,
    val prefilledBreakpointRule: BreakpointRule? = null,
    val prefilledBreakpointProtocolValues: List<ProtocolCriteriaValue> = emptyList(),
) {
    /**
     * Selected transaction UI model matching [selectedTransactionId].
     */
    val selectedTransaction: TrafficRowUiState?
        get() = transactions.find { it.transactionId == selectedTransactionId }
            ?: filteredTransactions.find { it.transactionId == selectedTransactionId }
            ?: transactions.firstOrNull()

    /**
     * Calculated statistics counts for quick protocol chips.
     */
    val httpCount: Int
        get() = transactions.count { row -> row.scheme.isStandard(StandardHttpScheme.HTTP) }

    val httpsCount: Int
        get() = transactions.count { row -> row.scheme.isStandard(StandardHttpScheme.HTTPS) }

    val http2Count: Int
        get() = transactions.count { row ->
            row.clientProtocol.isStandard(StandardApplicationProtocol.HTTP_2) ||
                row.upstreamProtocol?.isStandard(StandardApplicationProtocol.HTTP_2) == true
        }

    /**
     * Calculated total transferred payload size string.
     */
    val formattedVisibleSize: String
        get() {
            val totalBytes = filteredTransactions.sumOf { transaction -> transaction.transferredBytes }
            return when {
                totalBytes >= 1024 * 1024 -> "${(totalBytes / (1024.0 * 1024.0) * 100).toLong() / 100.0} MB"
                totalBytes >= 1024 -> "${(totalBytes / 1024.0 * 100).toLong() / 100.0} KB"
                else -> "$totalBytes B"
            }
        }
}

private fun HttpScheme.isStandard(expected: StandardHttpScheme): Boolean =
    this is HttpScheme.Standard && value == expected

private fun ApplicationProtocol.isStandard(expected: StandardApplicationProtocol): Boolean =
    this is ApplicationProtocol.Standard && value == expected
