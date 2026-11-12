package com.devuloopers.knet.ui.desktop.traffic.inspector

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.domain.clientNetwork.model.HttpVersionPreference
import com.devuloopers.knet.domain.network.model.NetworkRequestSpec
import com.devuloopers.knet.domain.util.UrlQueryStringParser
import com.devuloopers.knet.traffic.model.absoluteUrl
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.components.tabs.KNetTab
import com.devuloopers.knet.ui.core.components.tabs.KNetTabRow
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.core.components.inspector.KNetInspectorSummaryHeader
import com.devuloopers.knet.ui.core.components.inspector.KNetInspectorParagraph
import com.devuloopers.knet.ui.desktop.httppanel.model.InspectorSubTab
import com.devuloopers.knet.ui.desktop.httppanel.components.GraphQLBodySubTab
import com.devuloopers.knet.ui.desktop.httppanel.model.GraphQLQueryAvailability
import com.devuloopers.knet.ui.desktop.httppanel.viewpanels.RequestViewPanel
import com.devuloopers.knet.ui.desktop.httppanel.viewpanels.ResponseViewPanel
import com.devuloopers.knet.ui.desktop.httppanel.viewpanels.TimelineViewPanel
import com.devuloopers.knet.ui.desktop.traffic.model.*

/**
 * Right-docked inspection panel using shared :ui:core design tokens and primitives.
 */
@Composable
fun TrafficInspectorPanel(
    selectedTransaction: TrafficRowUiState?,
    activeTab: InspectorTab,
    activeRequestSubTab: InspectorSubTab = InspectorSubTab.BODY,
    activeResponseSubTab: InspectorSubTab = InspectorSubTab.BODY,
    preparedState: InspectorPreparedState = InspectorPreparedState(),
    protocolMessages: ProtocolMessagesUiState = ProtocolMessagesUiState(),
    onTabSelected: (InspectorTab) -> Unit,
    onRequestSubTabSelected: (InspectorSubTab) -> Unit = {},
    onResponseSubTabSelected: (InspectorSubTab) -> Unit = {},
    onProtocolMessageSelected: (com.devuloopers.knet.traffic.id.ProtocolMessageId) -> Unit = {},
    onLoadMoreProtocolMessages: () -> Unit = {},
    onSendToApiStudio: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    protectionState: TrafficProtectionState = TrafficProtectionState(),
    protectionActions: TrafficProtectionActions? = null,
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    var graphQLRequestSubTab by remember(selectedTransaction?.transactionId) { mutableStateOf(GraphQLBodySubTab.QUERY) }
    val selectedPrepared = selectedTransaction?.let { preparedState.forSelection(it.transactionId) }
        ?: InspectorPreparedState()
    val selectedMessages = selectedTransaction?.let { protocolMessages.forSelection(it.transactionId) }
        ?: ProtocolMessagesUiState()
    val presentation = if (selectedTransaction != null && selectedTransaction.rowKind == TrafficRowKind.HTTP_EXCHANGE)
        remember(selectedTransaction, selectedPrepared.exchange, selectedPrepared.annotations, selectedMessages.exchangeId,
            selectedMessages.totalCount, selectedMessages.items.map { it.protocol }.distinct()) {
            inspectorPresentation(selectedTransaction, selectedPrepared, selectedMessages)
        } else null
    val supportsMessages = presentation?.protocol?.streaming == true || selectedMessages.totalCount > 0L
    val shownTab = if (activeTab == InspectorTab.MESSAGES && !supportsMessages) InspectorTab.OVERVIEW else activeTab

    KNetSurface(
        modifier = modifier.fillMaxSize().border(width = 1.dp, color = themeColors.border),
        color = themeColors.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            presentation?.let { KNetInspectorSummaryHeader(it.summary) }
            // Top Inspector Navigation Header
            if (selectedTransaction?.rowKind != TrafficRowKind.OPAQUE_FLOW) {
                KNetTabRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(width = 1.dp, color = themeColors.border)
                ) {
                    KNetTab(
                        title = "Overview",
                        selected = shownTab == InspectorTab.OVERVIEW,
                        onClick = { onTabSelected(InspectorTab.OVERVIEW) }
                    )
                    KNetTab(
                        title = "Request",
                        selected = shownTab == InspectorTab.REQUEST,
                        onClick = { onTabSelected(InspectorTab.REQUEST) }
                    )
                    KNetTab(
                        title = "Response",
                        selected = shownTab == InspectorTab.RESPONSE,
                        onClick = { onTabSelected(InspectorTab.RESPONSE) }
                    )
                    if (supportsMessages) {
                        KNetTab(
                            title = if (presentation?.protocol == TrafficInspectorProtocol.SSE) "Events" else "Messages (${selectedMessages.totalCount})",
                            selected = shownTab == InspectorTab.MESSAGES,
                            onClick = { onTabSelected(InspectorTab.MESSAGES) },
                        )
                    }
                    KNetTab(
                        title = "Timeline",
                        selected = shownTab == InspectorTab.TIMELINE,
                        onClick = { onTabSelected(InspectorTab.TIMELINE) }
                    )
                }
            }

            if (selectedTransaction?.rowKind == TrafficRowKind.HTTP_EXCHANGE) {
                val preview = when (shownTab) {
                    InspectorTab.REQUEST -> if (selectedPrepared.requestBodyTruncated) "Request body preview truncated. Only retained bytes are shown." else null
                    InspectorTab.RESPONSE -> if (selectedPrepared.responseBodyTruncated) "Response body preview truncated. Only retained bytes are shown." else null
                    else -> null
                }
                preview?.let { KNetInspectorParagraph(it, Modifier.fillMaxWidth().padding(KNetTheme.spacing.md), color = themeColors.semantic.warning) }
            }

            // Tab Content Body
            if (selectedTransaction == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Select a transaction to inspect details",
                        style = typography.bodyMedium.copy(color = themeColors.textMuted)
                    )
                }
            } else if (selectedTransaction.rowKind == TrafficRowKind.OPAQUE_FLOW) {
                OpaqueFlowInspectorPanel(selectedTransaction, protectionState, protectionActions, Modifier.fillMaxSize())
            } else {
                val selectedPreparedState = selectedPrepared.takeIf { it.transactionId == selectedTransaction.transactionId }
                    ?: InspectorPreparedState.loading(selectedTransaction.transactionId)
                when (shownTab) {
                    InspectorTab.OVERVIEW -> presentation?.let {
                        TrafficOverviewPanel(selectedTransaction.transactionId, it, Modifier.fillMaxSize(), onViewQuery = {
                            if (it.graphQLQueryAvailability == GraphQLQueryAvailability.AVAILABLE) {
                                graphQLRequestSubTab = GraphQLBodySubTab.QUERY
                                onRequestSubTabSelected(if (selectedPrepared.exchange?.request?.head?.method?.token.equals("GET", true))
                                    InspectorSubTab.PARAMS else InspectorSubTab.BODY)
                                onTabSelected(InspectorTab.REQUEST)
                            }
                        })
                    }

                    InspectorTab.REQUEST -> {
                        val requestSpec = remember(selectedTransaction, selectedPreparedState) {
                            val canonicalRequest = selectedPreparedState.exchange?.request
                            val targetUrl = canonicalRequest?.absoluteUrl()
                                ?.takeIf { url -> "://" in url }
                                ?: selectedTransaction.fullUrl
                            val headerPairs = canonicalRequest?.head?.headers.orEmpty().map { header ->
                                header.name.value to header.value
                            }

                            NetworkRequestSpec(
                                method = canonicalRequest?.head?.method
                                    ?: com.devuloopers.knet.traffic.model.http.HttpMethod.fromToken(selectedTransaction.method),
                                httpVersionPreference = canonicalRequest?.head?.protocol
                                    ?.let(HttpVersionPreference::fromProtocol)
                                    ?: HttpVersionPreference.AUTO,
                                url = targetUrl,
                                headers = headerPairs,
                                queryParams = UrlQueryStringParser.parseQueryParams(targetUrl),
                                bodyPayload = selectedPreparedState.requestBodyText,
                                timestamp = selectedTransaction.timestamp
                            )
                        }
                        RequestViewPanel(
                            spec = requestSpec,
                            payloadSpec = selectedPreparedState.requestPayloadSpec.takeIf { !it.isEmpty },
                            parameterPayloadSpec = selectedPreparedState.requestParameterSpec,
                            isPreparing = selectedPreparedState.isPreparing,
                            activeSubTab = activeRequestSubTab,
                            onSubTabSelected = onRequestSubTabSelected,
                            showSummaryHeader = false,
                            graphQLSubTab = graphQLRequestSubTab,
                            onGraphQLSubTabSelected = { graphQLRequestSubTab = it },
                            onOpenInApiStudio = {
                                onSendToApiStudio(selectedTransaction.transactionId)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    InspectorTab.RESPONSE -> {
                        val responseHead = selectedPreparedState.exchange?.response?.head
                        ResponseViewPanel(
                            head = responseHead,
                            timings = selectedTransaction.timings,
                            showSummaryHeader = false,
                            responseSizeBytes = selectedTransaction.responseBytes,
                            payloadSpec = selectedPreparedState.responsePayloadSpec.takeIf { !it.isEmpty }
                                ?: com.devuloopers.knet.ui.desktop.httppanel.model.PayloadInspectionSpec.EMPTY,
                            isPreparing = selectedPreparedState.isPreparing,
                            activeSubTab = activeResponseSubTab,
                            onSubTabSelected = onResponseSubTabSelected,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    InspectorTab.MESSAGES -> {
                        ProtocolMessagesPanel(
                            state = selectedMessages,
                            events = presentation?.protocol == TrafficInspectorProtocol.SSE,
                            onMessageSelected = onProtocolMessageSelected,
                            onLoadMore = onLoadMoreProtocolMessages,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    InspectorTab.TIMELINE -> {
                        TimelineViewPanel(
                            timings = selectedTransaction.timings,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
