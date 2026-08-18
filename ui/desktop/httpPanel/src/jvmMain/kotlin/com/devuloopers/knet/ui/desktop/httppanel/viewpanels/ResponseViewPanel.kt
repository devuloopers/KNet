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
import com.devuloopers.knet.domain.clientNetwork.model.NetworkFailureReason
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.http.ResponseHead
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.components.keyvalue.KNetReadOnlyKeyValueViewer
import com.devuloopers.knet.ui.core.components.keyvalue.KeyValueEntry
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.httppanel.components.InspectorSubTabRow
import com.devuloopers.knet.ui.desktop.httppanel.components.NetworkErrorCard
import com.devuloopers.knet.ui.desktop.httppanel.components.ResponseSummaryHeader
import com.devuloopers.knet.ui.desktop.httppanel.components.SmartBodyViewer
import com.devuloopers.knet.ui.desktop.httppanel.model.PayloadInspectionSpec
import com.devuloopers.knet.ui.desktop.httppanel.model.InspectorSubTab

/**
 * Universal read-only HTTP response inspector panel.
 *
 * Renders status headers, timing/size metrics, response sub-tabs, formatted body viewing
 * via [SmartBodyViewer], headers key-value inspection, and cookie viewers.
 *
 * @param head Canonical response metadata, or null before a response is available.
 * @param timings Canonical exchange timing values.
 * @param isPreparing True if response payload is actively loading or being formatted.
 * @param activeSubTab Currently selected response sub-tab.
 * @param onSubTabSelected Event callback when user switches sub-tabs.
 * @param onClearResponse Optional callback to clear the response state.
 * @param modifier Composable layout modifier.
 */
@Composable
fun ResponseViewPanel(
    head: ResponseHead?,
    timings: ExchangeTimings = ExchangeTimings(),
    responseSizeBytes: Long = 0L,
    payloadSpec: PayloadInspectionSpec = PayloadInspectionSpec.EMPTY,
    cookies: List<Pair<String, String>> = emptyList(),
    failureReason: NetworkFailureReason? = null,
    errorMessage: String? = null,
    isPreparing: Boolean = false,
    activeSubTab: InspectorSubTab = InspectorSubTab.BODY,
    onSubTabSelected: (InspectorSubTab) -> Unit = {},
    onClearResponse: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    var localActiveTab by remember(activeSubTab) { mutableStateOf(activeSubTab) }

    val formattedSize = remember(responseSizeBytes) {
        when {
            responseSizeBytes <= 0 -> "0 B"
            responseSizeBytes < 1024 -> "$responseSizeBytes B"
            responseSizeBytes < 1024 * 1024 -> "${responseSizeBytes / 1024} KB"
            else -> "${responseSizeBytes / (1024 * 1024)} MB"
        }
    }

    val responseHeaders = head?.headers.orEmpty()
    val contentType = remember(responseHeaders) {
        responseHeaders.find { it.name.value.equals("content-type", ignoreCase = true) }?.value.orEmpty()
    }

    val headerEntries = remember(responseHeaders) {
        responseHeaders.mapIndexed { index, field ->
            KeyValueEntry("res_header_$index", field.name.value, field.value)
        }
    }

    val cookieEntries = remember(responseHeaders, cookies) {
        val values = cookies.ifEmpty {
            responseHeaders
                .filter { it.name.value.equals("set-cookie", ignoreCase = true) }
                .map { "Set-Cookie" to it.value }
        }
        values.mapIndexed { index, (name, value) -> KeyValueEntry("res_cookie_$index", name, value) }
    }

    val statusCode = head?.status?.code ?: 0
    val isGatewayError = statusCode == 502 || statusCode == 503 || statusCode == 504
    val isError = failureReason != null || isGatewayError || (head == null && !errorMessage.isNullOrBlank())
    val hasResponse = (head != null || payloadSpec.rawBody.isNotBlank()) && !isGatewayError

    if (isError) {
        NetworkErrorCard(
            failureReason = failureReason,
            errorMessage = errorMessage,
            onClearResponse = onClearResponse,
            modifier = modifier
        )
    } else if (!hasResponse) {
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
                head = requireNotNull(head),
                timings = timings,
                formattedSize = formattedSize,
                contentType = contentType,
                responseBody = payloadSpec.formattedText,
                cookies = cookieEntries.map { it.key to it.value },
                onClearResponse = onClearResponse
            )

            // 2. Response Sub-Tabs Row
            InspectorSubTabRow(
                tabs = InspectorSubTab.ResponseInspectorTabs,
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
                        val effectiveBodySpec = remember(payloadSpec, isPreparing) {
                            payloadSpec.copy(isPreparing = isPreparing || payloadSpec.isPreparing)
                        }
                        SmartBodyViewer(
                            spec = effectiveBodySpec,
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
