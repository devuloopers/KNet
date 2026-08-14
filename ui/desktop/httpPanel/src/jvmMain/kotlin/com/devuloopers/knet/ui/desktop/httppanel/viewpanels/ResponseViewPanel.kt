package com.devuloopers.knet.ui.desktop.httppanel.viewpanels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.domain.network.model.NetworkResponseSpec
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.components.keyvalue.KNetReadOnlyKeyValueViewer
import com.devuloopers.knet.ui.core.components.keyvalue.KeyValueEntry
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.httppanel.components.InspectorSubTabRow
import com.devuloopers.knet.ui.desktop.httppanel.components.NetworkErrorCard
import com.devuloopers.knet.ui.desktop.httppanel.components.ResponseSummaryHeader
import com.devuloopers.knet.ui.desktop.httppanel.components.SmartBodyViewer
import com.devuloopers.knet.ui.desktop.httppanel.model.BodyInspectionSpec
import com.devuloopers.knet.ui.desktop.httppanel.model.InspectorSubTab

/**
 * Universal read-only HTTP response inspector panel.
 *
 * Renders status headers, timing/size metrics, response sub-tabs, formatted body viewing
 * via [SmartBodyViewer], headers key-value inspection, and cookie viewers.
 *
 * @param spec Strongly-typed domain response specification.
 * @param isPreparing True if response payload is actively loading or being formatted.
 * @param activeSubTab Currently selected response sub-tab.
 * @param onSubTabSelected Event callback when user switches sub-tabs.
 * @param onClearResponse Optional callback to clear the response state.
 * @param modifier Composable layout modifier.
 */
@Composable
public fun ResponseViewPanel(
    spec: NetworkResponseSpec,
    isPreparing: Boolean = false,
    activeSubTab: InspectorSubTab = InspectorSubTab.BODY,
    onSubTabSelected: (InspectorSubTab) -> Unit = {},
    onClearResponse: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    var localActiveTab by remember(activeSubTab) { mutableStateOf(activeSubTab) }

    val formattedSize = remember(spec.sizeBytes) {
        when {
            spec.sizeBytes <= 0 -> "0 B"
            spec.sizeBytes < 1024 -> "${spec.sizeBytes} B"
            spec.sizeBytes < 1024 * 1024 -> "${spec.sizeBytes / 1024} KB"
            else -> "${spec.sizeBytes / (1024 * 1024)} MB"
        }
    }

    val contentType = remember(spec.headers) {
        spec.headers.find { it.first.equals("content-type", ignoreCase = true) }?.second ?: ""
    }

    val headerEntries = remember(spec.headers) {
        spec.headers.mapIndexed { idx, (k, v) -> KeyValueEntry("res_header_$idx", k, v) }
    }

    val cookieEntries = remember(spec.headers) {
        spec.headers
            .filter { it.first.equals("set-cookie", ignoreCase = true) }
            .mapIndexed { idx, (_, v) -> KeyValueEntry("res_cookie_$idx", "Set-Cookie", v) }
    }

    if (spec.isError) {
        NetworkErrorCard(
            failureReason = spec.failureReason,
            errorMessage = spec.errorMessage,
            onClearResponse = onClearResponse,
            modifier = modifier
        )
    } else if (!spec.hasResponse && spec.statusCode == 0 && spec.responseBody.isBlank()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(themeColors.surface),
            contentAlignment = Alignment.Center
        ) {
            val emptyTitle = "No Response Received"
            val emptyMessage = "Send a request in API Studio or select a network transaction to inspect its response."

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = emptyTitle,
                    style = typography.titleMedium.copy(
                        color = themeColors.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    text = emptyMessage,
                    style = typography.bodySmall.copy(color = themeColors.textMuted)
                )
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(themeColors.surface)
        ) {
            // 1. Response Summary Bar
            ResponseSummaryHeader(
                spec = spec,
                formattedSize = formattedSize,
                contentType = contentType,
                onClearResponse = onClearResponse
            )

            // 2. Response Sub-Tabs Row
            InspectorSubTabRow(
                tabs = InspectorSubTab.ResponseTabs,
                activeTab = localActiveTab,
                onTabSelected = { newTab ->
                    localActiveTab = newTab
                    onSubTabSelected(newTab)
                },
                headerCount = headerEntries.size,
                cookieCount = cookieEntries.size
            )

            HorizontalDivider(color = themeColors.border)

            // 3. Sub-Tab Panel Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (localActiveTab) {
                    InspectorSubTab.BODY -> {
                        val bodySpec = remember(spec.headers, spec.responseBody, isPreparing) {
                            BodyInspectionSpec(
                                headers = spec.headers,
                                rawBody = spec.responseBody,
                                isPreparing = isPreparing
                            )
                        }
                        SmartBodyViewer(
                            spec = bodySpec,
                            emptyTitle = "No Response Body",
                            emptySubtitle = "This response returned no body payload (e.g. HTTP 204 No Content or HTTP 304 Not Modified)",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    InspectorSubTab.HEADERS -> {
                        KNetReadOnlyKeyValueViewer(
                            entries = headerEntries,
                            keyHeader = "HEADER NAME",
                            valueHeader = "VALUE",
                            emptyMessage = "This response contained no HTTP header key-value pairs.",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    InspectorSubTab.COOKIES -> {
                        KNetReadOnlyKeyValueViewer(
                            entries = cookieEntries,
                            keyHeader = "COOKIE NAME",
                            valueHeader = "VALUE",
                            emptyMessage = "This response set no HTTP cookies (Set-Cookie headers).",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    InspectorSubTab.PARAMS -> {
                        // Params not applicable to response
                    }

                    else -> {}
                }
            }
        }
    }
}
