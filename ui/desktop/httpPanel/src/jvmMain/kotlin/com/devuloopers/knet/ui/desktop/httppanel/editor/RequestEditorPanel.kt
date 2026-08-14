package com.devuloopers.knet.ui.desktop.httppanel.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.engine.script.api.ScriptLanguage
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.components.keyvalue.KNetKeyValueEditor
import com.devuloopers.knet.ui.core.components.keyvalue.KeyValueEntry
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.httppanel.components.InspectorSubTabRow
import com.devuloopers.knet.ui.desktop.httppanel.model.*

/**
 * Cohesive actions parameter object for [RequestEditorPanel].
 */
data class RequestEditorPanelActions(
    val onBodyStateChanged: (RequestBodyState) -> Unit = {},
    val onBodyPayloadChanged: (String) -> Unit = {},
    val onGraphQlStateChanged: ((GraphQlState) -> Unit)? = null,
    val onQueryParamsChanged: (List<Pair<String, String>>) -> Unit = {},
    val onHeadersChanged: (List<Pair<String, String>>) -> Unit = {},
    val onCookiesChanged: (List<Pair<String, String>>) -> Unit = {},
    val onAuthStateChanged: (AuthState) -> Unit = {},
    val onPreRequestScriptChanged: (String) -> Unit = {},
    val onTestScriptChanged: (String) -> Unit = {},
    val onSubTabSelected: (InspectorSubTab) -> Unit = {},
    val onScriptPhaseSelected: (ScriptPhase) -> Unit = {},
    val onScriptLanguageChanged: (ScriptLanguage) -> Unit = {}
)

/**
 * Unified interactive HTTP request editor facade composable shared across API Studio payload authoring
 * and Breakpoint in-flight request modification.
 *
 * Renders standardized edge-to-edge sub-tabs, header key-value editors, body authoring,
 * auth state configuration, and pre/post-request scripting views.
 */
@Composable
fun RequestEditorPanel(
    bodyState: RequestBodyState = RequestBodyState(),
    queryParams: List<Pair<String, String>> = emptyList(),
    headers: List<Pair<String, String>> = emptyList(),
    cookies: List<Pair<String, String>> = emptyList(),
    authState: AuthState = AuthState(),
    preRequestScript: String = "",
    testScript: String = "",
    activeSubTab: InspectorSubTab = InspectorSubTab.BODY,
    activeScriptPhase: ScriptPhase = ScriptPhase.PRE_REQUEST,
    scriptLanguage: ScriptLanguage = ScriptLanguage.JAVASCRIPT,
    actions: RequestEditorPanelActions = RequestEditorPanelActions(),
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val spacing = KNetTheme.spacing

    var localActiveTab by remember(activeSubTab) { mutableStateOf(activeSubTab) }

    val headerEntries = remember(headers) {
        headers.mapIndexed { idx, (k, v) -> KeyValueEntry(id = "header_$idx", key = k, value = v) }
    }

    val paramEntries = remember(queryParams) {
        queryParams.mapIndexed { idx, (k, v) -> KeyValueEntry(id = "param_$idx", key = k, value = v) }
    }

    val cookieEntries = remember(cookies) {
        cookies.mapIndexed { idx, (k, v) -> KeyValueEntry(id = "cookie_$idx", key = k, value = v) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 1. Sub-Tabs Header Navigation Bar
        InspectorSubTabRow(
            tabs = InspectorSubTab.RequestTabs,
            activeTab = localActiveTab,
            onTabSelected = { newTab ->
                localActiveTab = newTab
                actions.onSubTabSelected(newTab)
            },
            headerCount = headers.size,
            paramCount = queryParams.size,
            cookieCount = cookies.size
        )

        HorizontalDivider(color = themeColors.border)

        // 2. Active Tab Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (localActiveTab) {
                InspectorSubTab.BODY -> {
                    RequestBodyEditor(
                        state = bodyState,
                        onStateChange = { updatedBodyState ->
                            actions.onBodyStateChanged(updatedBodyState)
                            actions.onBodyPayloadChanged(updatedBodyState.payloadText)
                        },
                        onGraphQlStateChange = actions.onGraphQlStateChanged,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                InspectorSubTab.HEADERS -> {
                    KNetKeyValueEditor(
                        entries = headerEntries,
                        keyHeader = "HEADER NAME",
                        valueHeader = "VALUE",
                        emptyMessage = "No HTTP headers configured. Click '+ Add Header' to start.",
                        addLabel = "Add Header",
                        onEntryChange = { entryIndex, updatedEntry ->
                            val updatedEntries = headerEntries.toMutableList().apply { set(entryIndex, updatedEntry) }
                            actions.onHeadersChanged(updatedEntries.map { it.key to it.value })
                        },
                        onAddEntry = {
                            val updatedList = headers + ("" to "")
                            actions.onHeadersChanged(updatedList)
                        },
                        onRemoveEntry = { targetIndex ->
                            val updatedList = headers.toMutableList().apply { removeAt(targetIndex) }
                            actions.onHeadersChanged(updatedList)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                InspectorSubTab.PARAMS -> {
                    KNetKeyValueEditor(
                        entries = paramEntries,
                        keyHeader = "PARAMETER NAME",
                        valueHeader = "VALUE",
                        emptyMessage = "No query parameters defined. Click '+ Add Param' to start.",
                        addLabel = "Add Param",
                        onEntryChange = { entryIndex, updatedEntry ->
                            val updatedEntries = paramEntries.toMutableList().apply { set(entryIndex, updatedEntry) }
                            actions.onQueryParamsChanged(updatedEntries.map { it.key to it.value })
                        },
                        onAddEntry = {
                            val updatedList = queryParams + ("" to "")
                            actions.onQueryParamsChanged(updatedList)
                        },
                        onRemoveEntry = { targetIndex ->
                            val updatedList = queryParams.toMutableList().apply { removeAt(targetIndex) }
                            actions.onQueryParamsChanged(updatedList)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                InspectorSubTab.COOKIES -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm)
                        ) {
                            Icon(
                                imageVector = KNetIcons.Info,
                                contentDescription = "Info",
                                modifier = Modifier.size(14.dp),
                                tint = themeColors.textMuted
                            )
                            Text(
                                text = "Cookies configured here are automatically formatted into the 'Cookie' header when sending requests.",
                                style = typography.caption.copy(color = themeColors.textMuted)
                            )
                        }

                        KNetKeyValueEditor(
                            entries = cookieEntries,
                            keyHeader = "COOKIE NAME",
                            valueHeader = "VALUE",
                            emptyMessage = "No cookies configured. Click '+ Add Cookie' to start.",
                            addLabel = "Add Cookie",
                            onEntryChange = { entryIndex, updatedEntry ->
                                val updatedEntries = cookieEntries.toMutableList().apply { set(entryIndex, updatedEntry) }
                                actions.onCookiesChanged(updatedEntries.map { it.key to it.value })
                            },
                            onAddEntry = {
                                val updatedList = cookies + ("" to "")
                                actions.onCookiesChanged(updatedList)
                            },
                            onRemoveEntry = { targetIndex ->
                                val updatedList = cookies.toMutableList().apply { removeAt(targetIndex) }
                                actions.onCookiesChanged(updatedList)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                InspectorSubTab.AUTH -> {
                    AuthEditor(
                        state = authState,
                        onStateChange = actions.onAuthStateChanged,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                InspectorSubTab.SCRIPTS -> {
                    ScriptEditor(
                        state = ScriptState(
                            preRequestScript = preRequestScript,
                            testScript = testScript,
                            scriptLanguage = scriptLanguage,
                            activePhase = activeScriptPhase
                        ),
                        onStateChange = { updated ->
                            actions.onPreRequestScriptChanged(updated.preRequestScript)
                            actions.onTestScriptChanged(updated.testScript)
                            actions.onScriptLanguageChanged(updated.scriptLanguage)
                            actions.onScriptPhaseSelected(updated.activePhase)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
