package com.devuloopers.knet.ui.desktop.httppanel.viewpanels

import com.devuloopers.knet.engine.formatter.model.BodyFormat
import com.devuloopers.knet.ui.desktop.httppanel.components.GraphQLBodyViewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.devuloopers.knet.domain.network.model.NetworkRequestSpec
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.components.keyvalue.KNetReadOnlyKeyValueViewer
import com.devuloopers.knet.ui.core.components.keyvalue.KeyValueEntry
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.httppanel.components.InspectorSubTabRow
import com.devuloopers.knet.ui.desktop.httppanel.components.RequestSummaryHeader
import com.devuloopers.knet.ui.desktop.httppanel.components.SmartBodyViewer
import com.devuloopers.knet.ui.desktop.httppanel.components.GraphQLBodySubTab
import com.devuloopers.knet.ui.desktop.httppanel.model.PayloadInspectionSpec
import com.devuloopers.knet.ui.desktop.httppanel.model.InspectorSubTab
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import androidx.compose.material3.Text

/**
 * Unified high-density request view panel composable shared across Live Traffic Inspector,
 * API Studio request panels, and live interception in-flight editing drawers.
 *
 * Streamlined view host in `com.devuloopers.knet.ui.desktop.httppanel.viewpanels` delegating to
 * modular viewers in `viewers/` and models in `model/`.
 *
 * @param spec Strongly-typed domain request specification.
 * @param isPreparing True if payload is asynchronously loading from disk/network.
 * @param activeSubTab Currently selected request sub-tab.
 * @param onSubTabSelected Event callback when user switches sub-tabs.
 * @param onOpenInApiStudio Optional action button callback for 1-click API Studio export.
 * @param modifier Composable layout modifier.
 * @param showSummaryHeader Whether this panel owns endpoint chrome; persistent Traffic headers render it once.
 * @param graphQLSubTab Optional controlled GraphQL body sub-tab for explicit query navigation.
 * @param onGraphQLSubTabSelected Publishes GraphQL sub-tab changes to the caller.
 */
@Composable
fun RequestViewPanel(
    spec: NetworkRequestSpec,
    payloadSpec: PayloadInspectionSpec? = null,
    parameterPayloadSpec: PayloadInspectionSpec = PayloadInspectionSpec.EMPTY,
    isPreparing: Boolean = false,
    activeSubTab: InspectorSubTab = InspectorSubTab.BODY,
    onSubTabSelected: (InspectorSubTab) -> Unit = {},
    onOpenInApiStudio: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    showSummaryHeader: Boolean = true,
    graphQLSubTab: GraphQLBodySubTab? = null,
    onGraphQLSubTabSelected: (GraphQLBodySubTab) -> Unit = {},
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
        if (showSummaryHeader) RequestSummaryHeader(
            spec = spec,
            onOpenInApiStudio = onOpenInApiStudio
        )
        // 2. Sub-Tabs Row
        InspectorSubTabRow(
            tabs = InspectorSubTab.RequestInspectorTabs,
            activeTab = localActiveTab,
            onTabSelected = { newTab ->
                localActiveTab = newTab
                onSubTabSelected(newTab)
            },
            headerCount = headerEntries.size,
            paramCount = paramEntries.size,
            cookieCount = cookieEntries.size,
            actions = if (!showSummaryHeader && onOpenInApiStudio != null) ({
                KNetButton(onOpenInApiStudio, variant = ButtonVariant.Ghost) {
                    Text("Open in API Studio", maxLines = 1, softWrap = false)
                }
            }) else null,
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
                    val effectiveBodySpec = remember(spec.headers, spec.bodyPayload, isPreparing, payloadSpec) {
                        payloadSpec ?: PayloadInspectionSpec(
                            headers = spec.headers,
                            rawBody = spec.bodyPayload,
                            isPreparing = isPreparing
                        )
                    }
                    SmartBodyViewer(
                        spec = effectiveBodySpec,
                        graphQLSubTab = graphQLSubTab,
                        onGraphQLSubTabSelected = onGraphQLSubTabSelected,
                        emptyTitle = "No Request Body",
                        emptySubtitle = "This request was sent without a body payload (e.g. GET or HEAD request)",
                        modifier = Modifier.fillMaxSize()
                    )
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
                    val graphQL = parameterPayloadSpec.resolvedFormat as? BodyFormat.GraphQL
                    if (graphQL != null) GraphQLBodyViewer(
                        format = graphQL, rawJsonText = "", rawParameters = paramEntries,
                        activeSubTab = graphQLSubTab, onSubTabSelected = onGraphQLSubTabSelected,
                    ) else KNetReadOnlyKeyValueViewer(
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
                        emptyMessage = "This request contained no HTTP cookies.",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                else -> {}
            }
        }
    }
}
