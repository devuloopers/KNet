package com.devuloopers.knet.ui.desktop.httppanel.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
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
import com.devuloopers.knet.ui.core.components.placeholder.KNetBodyLoadingPlaceholder
import com.devuloopers.knet.ui.core.components.placeholder.KNetEmptyStatePlaceholder
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.codeeditor.api.EditorMode
import com.devuloopers.knet.ui.desktop.codeeditor.api.KNetCodeEditor
import com.devuloopers.knet.ui.desktop.codeeditor.model.PreparedDocument
import com.devuloopers.knet.ui.desktop.httppanel.components.InspectorSubTabRow
import com.devuloopers.knet.ui.desktop.httppanel.components.NetworkExecutionErrorCard
import com.devuloopers.knet.ui.desktop.httppanel.components.ResponseSummaryHeader
import com.devuloopers.knet.ui.desktop.httppanel.model.InspectorSubTab

/**
 * Unified high-density response inspector composable shared across Live Traffic Feed,
 * API Studio execution output, and live interception response viewers.
 *
 * Streamlined view host in `com.devuloopers.knet.ui.desktop.http.view` delegating to
 * modular sub-components in `components/` and models in `model/`.
 *
 * @param spec Strongly-typed domain response specification.
 * @param preparedBody Optional pre-processed document model produced asynchronously off-thread.
 * @param isPreparing True if background body preparation is currently running.
 * @param activeSubTab Currently selected response sub-tab.
 * @param onSubTabSelected Event callback when user switches sub-tabs.
 * @param onClearResponse Optional event callback when user clears response output.
 * @param emptyMessage Description text displayed when no response is present.
 * @param modifier Composable layout modifier.
 */
@Composable
fun KNetResponseInspector(
    spec: NetworkResponseSpec,
    preparedBody: PreparedDocument? = null,
    isPreparing: Boolean = false,
    activeSubTab: InspectorSubTab = InspectorSubTab.BODY,
    onSubTabSelected: (InspectorSubTab) -> Unit = {},
    onClearResponse: (() -> Unit)? = null,
    emptyMessage: String = "Select a transaction or execute a request to view response details",
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    var localActiveTab by remember(activeSubTab) { mutableStateOf(activeSubTab) }

    val formattedSize = remember(spec.sizeBytes) {
        val kb = spec.sizeBytes / 1024.0
        if (kb > 0) "${(kb * 100).toInt() / 100.0} KB" else "${spec.sizeBytes} B"
    }

    val headerEntries = remember(spec.headers) {
        spec.headers.mapIndexed { index, (key, value) ->
            KeyValueEntry("resp_header_$index", key, value)
        }
    }

    val cookieEntries = remember(spec.cookies) {
        spec.cookies.mapIndexed { index, (key, value) ->
            KeyValueEntry("resp_cookie_$index", key, value)
        }
    }

    val contentType = remember(spec.headers) {
        spec.headers.find { it.first.equals("content-type", ignoreCase = true) }?.second
    }

    if (isPreparing) {
        KNetBodyLoadingPlaceholder(modifier = modifier)
    } else if (spec.isError) {
        NetworkExecutionErrorCard(
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = KNetIcons.Send,
                    contentDescription = "No Response",
                    modifier = Modifier.size(36.dp),
                    tint = themeColors.textMuted
                )
                Text(
                    text = "No Response Output",
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
                        if (preparedBody != null) {
                            val activeText = preparedBody.formattedText.ifBlank { preparedBody.rawText }
                            if (activeText.isBlank() && preparedBody.previewText.isBlank()) {
                                KNetEmptyStatePlaceholder(
                                    title = "No Response Body",
                                    subtitle = "This response returned no body payload (e.g. HTTP 204 No Content or HTTP 304 Not Modified)"
                                )
                            } else {
                                KNetCodeEditor(
                                    document = preparedBody,
                                    mode = EditorMode.ReadOnly,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else if (spec.responseBody.isBlank()) {
                            KNetEmptyStatePlaceholder(
                                title = "No Response Body",
                                subtitle = "This response returned no body payload (e.g. HTTP 204 No Content or HTTP 304 Not Modified)"
                            )
                        } else {
                            val langHint = when {
                                spec.responseBody.trimStart().startsWith("{") || spec.responseBody.trimStart().startsWith("[") -> "json"
                                spec.responseBody.trimStart().startsWith("<") -> "html"
                                else -> "plain"
                            }

                            KNetCodeEditor(
                                code = spec.responseBody,
                                mode = EditorMode.ReadOnly,
                                languageHint = langHint,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
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
                            emptyMessage = "This response included no Set-Cookie headers.",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    InspectorSubTab.PARAMS -> {
                        // Params not applicable to response
                    }
                }
            }
        }
    }
}
