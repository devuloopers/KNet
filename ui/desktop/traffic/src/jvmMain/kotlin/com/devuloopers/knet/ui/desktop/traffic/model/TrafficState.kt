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
import com.devuloopers.knet.application.port.traffic.ProtocolMessagePageCursor
import com.devuloopers.knet.application.port.traffic.ProtocolMessagePresentation
import com.devuloopers.knet.traffic.id.ProtocolMessageId
import com.devuloopers.knet.traffic.model.message.ProtocolMessageSnapshot

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
    MESSAGES,
    TIMELINE
}

/** Bounded presentation state for framed child messages belonging to the selected exchange. */
data class ProtocolMessagesUiState(
    val exchangeId: String = "",
    val items: List<ProtocolMessageSnapshot> = emptyList(),
    val totalCount: Long = 0L,
    val nextCursor: ProtocolMessagePageCursor? = null,
    val selectedMessageId: ProtocolMessageId? = null,
    val selectedBodyBytes: ByteArray? = null,
    val selectedBodyPresentation: ProtocolMessagePresentation? = null,
    val selectedBodyUnavailable: Boolean = false,
    val selectedBodyTruncated: Boolean = false,
    val isLoading: Boolean = false,
    val isBodyLoading: Boolean = false,
) {
    val selectedMessage: ProtocolMessageSnapshot?
        get() = items.firstOrNull { message -> message.id == selectedMessageId }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProtocolMessagesUiState) return false
        return exchangeId == other.exchangeId &&
            items == other.items &&
            totalCount == other.totalCount &&
            nextCursor == other.nextCursor &&
            selectedMessageId == other.selectedMessageId &&
            selectedBodyBytes.contentEqualsNullable(other.selectedBodyBytes) &&
            selectedBodyPresentation == other.selectedBodyPresentation &&
            selectedBodyUnavailable == other.selectedBodyUnavailable &&
            selectedBodyTruncated == other.selectedBodyTruncated &&
            isLoading == other.isLoading &&
            isBodyLoading == other.isBodyLoading
    }

    override fun hashCode(): Int {
        var result = exchangeId.hashCode()
        result = 31 * result + items.hashCode()
        result = 31 * result + totalCount.hashCode()
        result = 31 * result + (nextCursor?.hashCode() ?: 0)
        result = 31 * result + (selectedMessageId?.hashCode() ?: 0)
        result = 31 * result + (selectedBodyBytes?.contentHashCode() ?: 0)
        result = 31 * result + (selectedBodyPresentation?.hashCode() ?: 0)
        result = 31 * result + selectedBodyUnavailable.hashCode()
        result = 31 * result + selectedBodyTruncated.hashCode()
        result = 31 * result + isLoading.hashCode()
        result = 31 * result + isBodyLoading.hashCode()
        return result
    }
}

private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean = when {
    this === other -> true
    this == null || other == null -> false
    else -> contentEquals(other)
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
    val protocolMessages: ProtocolMessagesUiState = ProtocolMessagesUiState(),
    val localIpAddress: String = "127.0.0.1",
    val activeBreakpointRules: List<BreakpointRule> = emptyList(),
    val isBreakpointDrawerVisible: Boolean = false,
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
