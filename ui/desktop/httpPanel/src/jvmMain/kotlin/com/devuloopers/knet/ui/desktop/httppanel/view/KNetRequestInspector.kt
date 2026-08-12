package com.devuloopers.knet.ui.desktop.httppanel.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.domain.network.model.NetworkRequestSpec
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.components.keyvalue.KNetReadOnlyKeyValueViewer
import com.devuloopers.knet.ui.core.components.keyvalue.KeyValueEntry
import com.devuloopers.knet.ui.core.components.placeholder.KNetEmptyStatePlaceholder
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.codeeditor.api.EditorMode
import com.devuloopers.knet.ui.desktop.codeeditor.api.KNetCodeEditor
import com.devuloopers.knet.ui.desktop.httppanel.components.InspectorSubTabRow
import com.devuloopers.knet.ui.desktop.httppanel.components.RequestSummaryHeader
import com.devuloopers.knet.ui.desktop.httppanel.model.InspectorSubTab

/**
 * Unified high-density request inspector composable shared across Live Traffic Inspector,
 * API Studio request panels, and live interception in-flight editing drawers.
 *
 * Streamlined view host in `com.devuloopers.knet.ui.desktop.http.view` delegating to
 * modular sub-components in `components/` and models in `model/`.
 *
 * @param spec Strongly-typed domain request specification.
 * @param activeSubTab Currently selected request sub-tab.
 * @param onSubTabSelected Event callback when user switches sub-tabs.
 * @param onOpenInApiStudio Optional action button callback for 1-click API Studio export.
 * @param modifier Composable layout modifier.
 */
@Composable
public fun KNetRequestInspector(
    spec: NetworkRequestSpec,
    activeSubTab: InspectorSubTab = InspectorSubTab.BODY,
    onSubTabSelected: (InspectorSubTab) -> Unit = {},
    onOpenInApiStudio: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors

    var localActiveTab by remember(activeSubTab) { mutableStateOf(activeSubTab) }

    val headerEntries = remember(spec.headers) {
        spec.headers.mapIndexed { idx, (k, v) -> KeyValueEntry("req_header_$idx", k, v) }
    }

    val paramEntries = remember(spec.queryParams) {
        spec.queryParams.mapIndexed { idx, (k, v) -> KeyValueEntry("req_param_$idx", k, v) }
    }

    val cookieEntries = remember(spec.cookies) {
        spec.cookies.mapIndexed { idx, (k, v) -> KeyValueEntry("req_cookie_$idx", k, v) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(themeColors.surface)
    ) {
        // 1. Target URL Summary Bar
        RequestSummaryHeader(
            spec = spec,
            onOpenInApiStudio = onOpenInApiStudio
        )

        // 2. Sub-Tabs Row
        InspectorSubTabRow(
            tabs = InspectorSubTab.RequestTabs,
            activeTab = localActiveTab,
            onTabSelected = { newTab ->
                localActiveTab = newTab
                onSubTabSelected(newTab)
            },
            headerCount = headerEntries.size,
            paramCount = paramEntries.size,
            cookieCount = cookieEntries.size
        )

        HorizontalDivider(color = themeColors.border)

        // 3. Sub-Tab Request Content Panel
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (localActiveTab) {
                InspectorSubTab.BODY -> {
                    if (spec.bodyPayload.isBlank()) {
                        KNetEmptyStatePlaceholder(
                            title = "No Request Body",
                            subtitle = "This request was sent without a body payload (e.g. GET or HEAD request)"
                        )
                    } else {
                        val langHint = when {
                            spec.bodyPayload.trimStart().startsWith("{") || spec.bodyPayload.trimStart().startsWith("[") -> "json"
                            spec.bodyPayload.trimStart().startsWith("<") -> "html"
                            else -> "plain"
                        }

                        KNetCodeEditor(
                            code = spec.bodyPayload,
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
                        emptyMessage = "This request contained no HTTP header key-value pairs.",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                InspectorSubTab.PARAMS -> {
                    KNetReadOnlyKeyValueViewer(
                        entries = paramEntries,
                        keyHeader = "PARAMETER NAME",
                        valueHeader = "VALUE",
                        emptyMessage = "This request URL contains no query parameters.",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                InspectorSubTab.COOKIES -> {
                    KNetReadOnlyKeyValueViewer(
                        entries = cookieEntries,
                        keyHeader = "COOKIE NAME",
                        valueHeader = "VALUE",
                        emptyMessage = "This request included no Cookie header.",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
