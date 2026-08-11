package com.devuloopers.knet.ui.desktop.codeeditor.inspector

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.devuloopers.knet.domain.network.model.NetworkRequestSpec
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.button.KNetCopyButton
import com.devuloopers.knet.ui.core.components.divider.VerticalDivider
import com.devuloopers.knet.ui.core.components.keyvalue.KNetReadOnlyKeyValueViewer
import com.devuloopers.knet.ui.core.components.keyvalue.KeyValueEntry
import com.devuloopers.knet.ui.core.components.placeholder.KNetEmptyStatePlaceholder
import com.devuloopers.knet.ui.core.components.tabs.KNetTab
import com.devuloopers.knet.ui.core.components.tabs.ScrollableTabRow
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.codeeditor.api.EditorMode
import com.devuloopers.knet.ui.desktop.codeeditor.api.KNetCodeEditor

/**
 * Closed set of sub-tabs supported by [KNetRequestEditorView].
 */
public enum class InspectorRequestSubTab(val label: String) {
    BODY("Body"),
    HEADERS("Headers"),
    PARAMS("Params"),
    COOKIES("Cookies")
}

/**
 * Unified high-density request editor/viewer composable shared across Live Traffic Inspector,
 * API Studio request panels, and live interception in-flight editing drawers.
 *
 * Implements Option B architecture (stateless composable powered directly by domain [NetworkRequestSpec]).
 *
 * @param spec Strongly-typed domain request specification.
 * @param activeSubTab Currently selected request sub-tab.
 * @param onSubTabSelected Event callback when user switches sub-tabs.
 * @param onOpenInApiStudio Optional action button callback for 1-click API Studio export.
 * @param modifier Composable layout modifier.
 */
@Composable
public fun KNetRequestEditorView(
    spec: NetworkRequestSpec,
    activeSubTab: InspectorRequestSubTab = InspectorRequestSubTab.BODY,
    onSubTabSelected: (InspectorRequestSubTab) -> Unit = {},
    onOpenInApiStudio: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    var localActiveTab by remember(activeSubTab) { mutableStateOf(activeSubTab) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(themeColors.surface)
    ) {
        // 1. Target URL Summary Bar
        val urlScrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(themeColors.surface)
                .border(width = 1.dp, color = themeColors.border)
                .horizontalScroll(urlScrollState)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = spec.methodString,
                style = typography.codeSmall.copy(
                    color = themeColors.accent,
                    fontWeight = FontWeight.Bold
                )
            )

            VerticalDivider(modifier = Modifier.height(16.dp))

            Text(
                text = spec.url.ifEmpty { "(No URL)" },
                style = typography.codeSmall.copy(color = themeColors.textPrimary)
            )

            Box(modifier = Modifier.weight(1f))

            if (onOpenInApiStudio != null) {
                KNetButton(
                    onClick = onOpenInApiStudio
                ) {
                    Text("Open in API Studio")
                }
            } else {
                KNetCopyButton(
                    textToCopy = spec.url
                )
            }
        }

        // 2. Sub-Tabs Row
        val tabsList = remember(spec.headers, spec.queryParams, spec.cookies) {
            listOf(
                InspectorRequestSubTab.BODY.label,
                "Headers (${spec.headers.size})",
                "Params (${spec.queryParams.size})",
                "Cookies (${spec.cookies.size})"
            )
        }

        ScrollableTabRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(themeColors.surface)
        ) {
            InspectorRequestSubTab.entries.forEachIndexed { index, subTab ->
                val titleText = tabsList[index]
                KNetTab(
                    title = titleText,
                    selected = localActiveTab == subTab,
                    onClick = {
                        localActiveTab = subTab
                        onSubTabSelected(subTab)
                    }
                )
            }
        }

        // 3. Sub-Tab Request Content Panel
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (localActiveTab) {
                InspectorRequestSubTab.BODY -> {
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
                InspectorRequestSubTab.HEADERS -> {
                    val entries = remember(spec.headers) {
                        spec.headers.mapIndexed { idx, (k, v) -> KeyValueEntry("rh_$idx", k, v) }
                    }
                    KNetReadOnlyKeyValueViewer(
                        entries = entries,
                        keyHeader = "HEADER NAME",
                        valueHeader = "VALUE",
                        emptyMessage = "This request contained no HTTP header key-value pairs.",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                InspectorRequestSubTab.PARAMS -> {
                    val entries = remember(spec.queryParams) {
                        spec.queryParams.mapIndexed { idx, (k, v) -> KeyValueEntry("qp_$idx", k, v) }
                    }
                    KNetReadOnlyKeyValueViewer(
                        entries = entries,
                        keyHeader = "PARAMETER NAME",
                        valueHeader = "VALUE",
                        emptyMessage = "This request URL contains no query parameters.",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                InspectorRequestSubTab.COOKIES -> {
                    val entries = remember(spec.cookies) {
                        spec.cookies.mapIndexed { idx, (k, v) -> KeyValueEntry("rc_$idx", k, v) }
                    }
                    KNetReadOnlyKeyValueViewer(
                        entries = entries,
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
