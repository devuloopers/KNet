package com.devuloopers.knet.ui.desktop.traffic.inspector

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.domain.clientNetwork.model.HttpVersionPreference
import com.devuloopers.knet.domain.network.model.NetworkRequestSpec
import com.devuloopers.knet.domain.util.UrlQueryStringParser
import com.devuloopers.knet.traffic.model.absoluteUrl
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.components.tabs.KNetTab
import com.devuloopers.knet.ui.core.components.tabs.KNetTabRow
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.httppanel.model.InspectorSubTab
import com.devuloopers.knet.ui.desktop.httppanel.model.NetworkOverviewSpec
import com.devuloopers.knet.ui.desktop.httppanel.viewpanels.OverviewViewPanel
import com.devuloopers.knet.ui.desktop.httppanel.viewpanels.RequestViewPanel
import com.devuloopers.knet.ui.desktop.httppanel.viewpanels.ResponseViewPanel
import com.devuloopers.knet.ui.desktop.httppanel.viewpanels.TimelineViewPanel
import com.devuloopers.knet.ui.desktop.traffic.model.*
import com.devuloopers.knet.traffic.inspection.InspectionAnnotation
import com.devuloopers.knet.traffic.inspection.InspectionAnnotationState

/**
 * 500dp Right-Docked Inspection Panel bound strictly to :ui:core design tokens and primitives.
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
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    KNetSurface(
        modifier = modifier.fillMaxSize().border(width = 1.dp, color = themeColors.border),
        color = themeColors.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Inspector Navigation Header
            KNetTabRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = themeColors.border)
            ) {
                KNetTab(
                    title = "Overview",
                    selected = activeTab == InspectorTab.OVERVIEW,
                    onClick = { onTabSelected(InspectorTab.OVERVIEW) }
                )
                KNetTab(
                    title = "Request",
                    selected = activeTab == InspectorTab.REQUEST,
                    onClick = { onTabSelected(InspectorTab.REQUEST) }
                )
                KNetTab(
                    title = "Response",
                    selected = activeTab == InspectorTab.RESPONSE,
                    onClick = { onTabSelected(InspectorTab.RESPONSE) }
                )
                if (protocolMessages.totalCount > 0L || activeTab == InspectorTab.MESSAGES) {
                    KNetTab(
                        title = "Messages (${protocolMessages.totalCount})",
                        selected = activeTab == InspectorTab.MESSAGES,
                        onClick = { onTabSelected(InspectorTab.MESSAGES) },
                    )
                }
                KNetTab(
                    title = "Timeline",
                    selected = activeTab == InspectorTab.TIMELINE,
                    onClick = { onTabSelected(InspectorTab.TIMELINE) }
                )
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
            } else {
                val selectedPreparedState = preparedState.forSelection(selectedTransaction.transactionId)
                    ?: InspectorPreparedState.loading(selectedTransaction.transactionId)
                val exchange = selectedPreparedState.exchange
                when (activeTab) {
                    InspectorTab.OVERVIEW -> {
                        val overviewSpec = remember(selectedTransaction, exchange) {
                            val targetUrl = exchange?.request?.absoluteUrl()
                                ?.takeIf { url -> "://" in url }
                                ?: selectedTransaction.fullUrl
                            val contentType = exchange?.response?.head?.headers
                                ?.firstOrNull { header ->
                                    header.name.value.equals("Content-Type", ignoreCase = true)
                                }
                                ?.value
                                .orEmpty()
                            NetworkOverviewSpec(
                                method = selectedTransaction.method,
                                url = targetUrl,
                                statusCode = selectedTransaction.status,
                                statusText = selectedTransaction.statusText,
                                isTerminal = selectedTransaction.terminalOutcome != null,
                                clientProtocol = exchange?.request?.head?.protocol?.token
                                    ?: selectedTransaction.clientProtocol.token,
                                upstreamProtocol = exchange?.response?.head?.protocol?.token
                                    ?: selectedTransaction.upstreamProtocol?.token,
                                origin = exchange?.origin?.displayName ?: selectedTransaction.origin.displayName,
                                connectionId = exchange?.connectionId?.value ?: selectedTransaction.connectionId,
                                streamId = exchange?.streamId?.value ?: selectedTransaction.streamId,
                                remoteIp = "",
                                timestamp = selectedTransaction.formattedTimestamp,
                                durationMs = selectedTransaction.formattedTime,
                                sizeBytes = selectedTransaction.formattedSize,
                                contentType = contentType
                            )
                        }
                        Column(modifier = Modifier.fillMaxSize()) {
                            OverviewViewPanel(
                                spec = overviewSpec,
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            )
                            if (selectedPreparedState.annotations.isNotEmpty()) {
                                SemanticAnnotationsPanel(
                                    annotations = selectedPreparedState.annotations,
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                                )
                            }
                        }
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
                            isPreparing = selectedPreparedState.isPreparing,
                            activeSubTab = activeRequestSubTab,
                            onSubTabSelected = onRequestSubTabSelected,
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
                            state = protocolMessages,
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

/** Protocol-neutral renderer; individual inspectors only contribute versioned generic documents. */
@Composable
private fun SemanticAnnotationsPanel(
    annotations: List<InspectionAnnotation>,
    modifier: Modifier = Modifier,
) {
    val colors = KNetTheme.colors
    val typography = KNetTheme.typography
    Column(
        modifier = modifier
            .border(1.dp, colors.border)
            .background(colors.surface)
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Semantic inspection", style = typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
        annotations.forEach { annotation ->
            val document = annotation.document
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = document?.title ?: annotation.inspectorId.value,
                    style = typography.bodyMedium.copy(
                        color = if (annotation.state == InspectionAnnotationState.FAILED) {
                            colors.semantic.error
                        } else {
                            colors.textPrimary
                        },
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                document?.summary?.takeIf(String::isNotBlank)?.let { summary ->
                    Text(summary, style = typography.codeSmall.copy(color = colors.textSecondary))
                }
                document?.fields.orEmpty().forEach { field ->
                    Text(
                        "${field.label}: ${field.value}",
                        style = typography.codeSmall.copy(color = colors.textSecondary),
                    )
                }
                annotation.errorCode?.let { error ->
                    Text(error, style = typography.codeSmall.copy(color = colors.textMuted))
                }
            }
        }
    }
}
