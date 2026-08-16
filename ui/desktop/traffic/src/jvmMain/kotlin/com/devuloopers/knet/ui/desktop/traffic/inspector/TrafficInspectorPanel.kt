package com.devuloopers.knet.ui.desktop.traffic.inspector

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import com.devuloopers.knet.domain.collection.model.HttpMethod
import com.devuloopers.knet.domain.network.model.NetworkRequestSpec
import com.devuloopers.knet.domain.network.model.NetworkResponseSpec
import com.devuloopers.knet.domain.traffic.model.TrafficItemUiState
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.components.tabs.KNetTab
import com.devuloopers.knet.ui.core.components.tabs.ScrollableTabRow
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.httppanel.model.InspectorSubTab
import com.devuloopers.knet.ui.desktop.httppanel.model.NetworkOverviewSpec
import com.devuloopers.knet.ui.desktop.httppanel.model.NetworkTimingSpec
import com.devuloopers.knet.ui.desktop.httppanel.viewpanels.OverviewViewPanel
import com.devuloopers.knet.ui.desktop.httppanel.viewpanels.RequestViewPanel
import com.devuloopers.knet.ui.desktop.httppanel.viewpanels.ResponseViewPanel
import com.devuloopers.knet.ui.desktop.httppanel.viewpanels.TimelineViewPanel
import com.devuloopers.knet.ui.desktop.traffic.model.*

/**
 * 500dp Right-Docked Inspection Panel bound strictly to :ui:core design tokens and primitives.
 */
@Composable
fun TrafficInspectorPanel(
    selectedTransaction: TrafficItemUiState?,
    activeTab: InspectorTab,
    activeRequestSubTab: RequestSubTab = RequestSubTab.BODY,
    activeResponseSubTab: ResponseSubTab = ResponseSubTab.BODY,
    previewMode: PreviewFormatMode,
    preparedState: InspectorPreparedState = InspectorPreparedState(),
    onTabSelected: (InspectorTab) -> Unit,
    onRequestSubTabSelected: (RequestSubTab) -> Unit = {},
    onResponseSubTabSelected: (ResponseSubTab) -> Unit = {},
    onPreviewModeSelected: (PreviewFormatMode) -> Unit,
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
            ScrollableTabRow(
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
                when (activeTab) {
                    InspectorTab.OVERVIEW -> {
                        val overviewSpec = remember(selectedTransaction) {
                            val targetUrl =
                                if (selectedTransaction.host.isNotBlank()) "https://${selectedTransaction.host}${selectedTransaction.path}" else selectedTransaction.path
                            val contentType =
                                selectedTransaction.responseHeaders.map { it.key to it.value }
                                    .find { it.first.equals("Content-Type", ignoreCase = true) }?.second ?: ""
                            NetworkOverviewSpec(
                                method = selectedTransaction.method,
                                url = targetUrl,
                                statusCode = selectedTransaction.status,
                                statusText = selectedTransaction.statusText,
                                protocol = selectedTransaction.protocol,
                                remoteIp = if (selectedTransaction.host.isNotBlank()) "${selectedTransaction.host}:443" else "",
                                timestamp = selectedTransaction.dateGroup.ifEmpty { selectedTransaction.timestamp.toString() },
                                durationMs = selectedTransaction.formattedTime,
                                sizeBytes = selectedTransaction.formattedSize,
                                contentType = contentType
                            )
                        }
                        OverviewViewPanel(
                            spec = overviewSpec,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    InspectorTab.REQUEST -> {
                        val requestSpec = remember(selectedTransaction, preparedState) {
                            val reqBody =
                                preparedState.requestBodyText.ifBlank { selectedTransaction.requestBody }
                            val headerPairs = selectedTransaction.requestHeaders.map { it.key to it.value }
                            val queryParamsList = selectedTransaction.queryParams.map { it.key to it.value.toString() }
                            val parsedMethod = try {
                                HttpMethod.valueOf(selectedTransaction.method.uppercase())
                            } catch (_: Exception) {
                                HttpMethod.CUSTOM
                            }
                            val targetUrl =
                                if (selectedTransaction.host.isNotBlank()) "https://${selectedTransaction.host}${selectedTransaction.path}" else selectedTransaction.path

                            NetworkRequestSpec(
                                method = parsedMethod,
                                customMethod = if (parsedMethod == HttpMethod.CUSTOM) selectedTransaction.method else null,
                                url = targetUrl,
                                headers = headerPairs,
                                queryParams = queryParamsList,
                                bodyPayload = reqBody,
                                timestamp = selectedTransaction.timestamp
                            )
                        }
                        val mappedReqSubTab = when (activeRequestSubTab) {
                            RequestSubTab.HEADERS -> InspectorSubTab.HEADERS
                            RequestSubTab.PARAMS -> InspectorSubTab.PARAMS
                            RequestSubTab.COOKIES -> InspectorSubTab.COOKIES
                            RequestSubTab.BODY -> InspectorSubTab.BODY
                        }
                        RequestViewPanel(
                            spec = requestSpec,
                            payloadSpec = preparedState.requestPayloadSpec.takeIf { !it.isEmpty },
                            isPreparing = preparedState.isPreparing,
                            activeSubTab = mappedReqSubTab,
                            onSubTabSelected = { newSubTab ->
                                val legacyTab = when (newSubTab) {
                                    InspectorSubTab.HEADERS -> RequestSubTab.HEADERS
                                    InspectorSubTab.PARAMS -> RequestSubTab.PARAMS
                                    InspectorSubTab.COOKIES -> RequestSubTab.COOKIES
                                    InspectorSubTab.BODY -> RequestSubTab.BODY
                                    else -> RequestSubTab.BODY
                                }
                                onRequestSubTabSelected(legacyTab)
                            },
                            onOpenInApiStudio = {
                                onSendToApiStudio(selectedTransaction.id.toString())
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    InspectorTab.RESPONSE -> {
                        val responseSpec = remember(selectedTransaction, preparedState) {
                            val resBody =
                                preparedState.responseBodyText.ifBlank { selectedTransaction.responseBody }
                            val headerPairs = selectedTransaction.responseHeaders.map { it.key to it.value }
                            NetworkResponseSpec(
                                statusCode = selectedTransaction.status,
                                statusText = selectedTransaction.statusText,
                                durationMs = parseDurationMs(selectedTransaction.formattedTime),
                                sizeBytes = parseSizeBytes(selectedTransaction.formattedSize),
                                responseBody = resBody,
                                headers = headerPairs
                            )
                        }
                        val mappedResSubTab = when (activeResponseSubTab) {
                            ResponseSubTab.HEADERS -> InspectorSubTab.HEADERS
                            ResponseSubTab.COOKIES -> InspectorSubTab.COOKIES
                            ResponseSubTab.BODY -> InspectorSubTab.BODY
                        }
                        ResponseViewPanel(
                            spec = responseSpec,
                            payloadSpec = preparedState.responsePayloadSpec.takeIf { !it.isEmpty },
                            isPreparing = preparedState.isPreparing,
                            activeSubTab = mappedResSubTab,
                            onSubTabSelected = { newSubTab ->
                                val legacyTab = when (newSubTab) {
                                    InspectorSubTab.HEADERS -> ResponseSubTab.HEADERS
                                    InspectorSubTab.COOKIES -> ResponseSubTab.COOKIES
                                    InspectorSubTab.BODY -> ResponseSubTab.BODY
                                    else -> ResponseSubTab.BODY
                                }
                                onResponseSubTabSelected(legacyTab)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    InspectorTab.TIMELINE -> {
                        val timingSpec = remember(selectedTransaction.timings) {
                            val t = selectedTransaction.timings
                            NetworkTimingSpec(
                                dnsMs = t.dnsMs,
                                tcpMs = t.tcpMs,
                                tlsMs = t.tlsMs,
                                ttfbMs = t.ttfbMs,
                                downloadMs = t.downloadMs,
                                totalTimeMs = t.totalTimeMs,
                                isReusedConnection = t.isReusedConnection
                            )
                        }
                        TimelineViewPanel(
                            spec = timingSpec,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

private fun parseDurationMs(formattedTime: String): Long {
    val numeric = formattedTime.filter { it.isDigit() }
    return numeric.toLongOrNull() ?: 0L
}

private fun parseSizeBytes(formattedSize: String): Long {
    val numeric = formattedSize.filter { it.isDigit() }
    return numeric.toLongOrNull() ?: 0L
}
