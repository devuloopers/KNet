package com.devuloopers.knet.ui.desktop.httppanel.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.scripting.model.ScriptLanguage
import com.devuloopers.knet.scripting.model.ScriptPhase
import com.devuloopers.knet.ui.core.components.keyvalue.KNetKeyValueEditor
import com.devuloopers.knet.ui.core.components.keyvalue.KeyValueEntry
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.httppanel.components.InspectorSubTabRow
import com.devuloopers.knet.ui.desktop.httppanel.model.*
import kotlin.uuid.Uuid

/**
 * Cohesive actions parameter object for [RequestEditorPanel].
 */
data class RequestEditorPanelActions(
    val onBodyStateChanged: (RequestBodyState) -> Unit = {},
    val onGraphQlStateChanged: ((GraphQlState) -> Unit)? = null,
    val onQueryParamsChanged: (List<KeyValueEntry>) -> Unit = {},
    val onHeadersChanged: (List<KeyValueEntry>) -> Unit = {},
    val onCookiesChanged: (List<KeyValueEntry>) -> Unit = {},
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
 * Renders standardized inset sub-tabs, request key-value editors, body authoring,
 * auth state configuration, and pre/post-request scripting views.
 */
@Composable
fun RequestEditorPanel(
    bodyState: RequestBodyState = RequestBodyState(),
    queryParams: List<KeyValueEntry> = emptyList(),
    headers: List<KeyValueEntry> = emptyList(),
    cookies: List<KeyValueEntry> = emptyList(),
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
    val shapes = KNetTheme.shapes
    val requestKeyValueEditorModifier = Modifier
        .fillMaxSize()
        .padding(horizontal = spacing.md, vertical = spacing.sm)
        .clip(shapes.medium)

    var localActiveTab by remember(activeSubTab) { mutableStateOf(activeSubTab) }

    Column(modifier = modifier.fillMaxSize()) {
        // 1. Sub-Tabs Header Navigation Bar
        InspectorSubTabRow(
            tabs = InspectorSubTab.RequestEditorTabs,
            activeTab = localActiveTab,
            onTabSelected = { newTab ->
                localActiveTab = newTab
                actions.onSubTabSelected(newTab)
            },
            headerCount = headers.size,
            paramCount = queryParams.size,
            cookieCount = cookies.size,
            modifier = Modifier.padding(horizontal = spacing.md)
        )

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
                        onStateChange = actions.onBodyStateChanged,
                        onGraphQlStateChange = actions.onGraphQlStateChanged,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                InspectorSubTab.HEADERS -> {
                    KNetKeyValueEditor(
                        entries = headers,
                        keyHeader = "HEADER NAME",
                        valueHeader = "VALUE",
                        emptyMessage = "No HTTP headers configured. Click '+ Add Header' to start.",
                        addLabel = "Add Header",
                        onEntryChange = { entryIndex, updatedEntry ->
                            val updatedEntries = headers.toMutableList().apply { set(entryIndex, updatedEntry) }
                            actions.onHeadersChanged(updatedEntries)
                        },
                        onAddEntry = {
                            val updatedList = headers + newEntry("header")
                            actions.onHeadersChanged(updatedList)
                        },
                        onRemoveEntry = { targetIndex ->
                            val updatedList = headers.toMutableList().apply { removeAt(targetIndex) }
                            actions.onHeadersChanged(updatedList)
                        },
                        modifier = requestKeyValueEditorModifier
                    )
                }

                InspectorSubTab.PARAMS -> {
                    KNetKeyValueEditor(
                        entries = queryParams,
                        keyHeader = "PARAMETER NAME",
                        valueHeader = "VALUE",
                        emptyMessage = "No query parameters defined. Click '+ Add Param' to start.",
                        addLabel = "Add Param",
                        onEntryChange = { entryIndex, updatedEntry ->
                            val updatedEntries = queryParams.toMutableList().apply { set(entryIndex, updatedEntry) }
                            actions.onQueryParamsChanged(updatedEntries)
                        },
                        onAddEntry = {
                            val updatedList = queryParams + newEntry("query")
                            actions.onQueryParamsChanged(updatedList)
                        },
                        onRemoveEntry = { targetIndex ->
                            val updatedList = queryParams.toMutableList().apply { removeAt(targetIndex) }
                            actions.onQueryParamsChanged(updatedList)
                        },
                        modifier = requestKeyValueEditorModifier
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
                            entries = cookies,
                            keyHeader = "COOKIE NAME",
                            valueHeader = "VALUE",
                            emptyMessage = "No cookies configured. Click '+ Add Cookie' to start.",
                            addLabel = "Add Cookie",
                            onEntryChange = { entryIndex, updatedEntry ->
                                val updatedEntries = cookies.toMutableList().apply { set(entryIndex, updatedEntry) }
                                actions.onCookiesChanged(updatedEntries)
                            },
                            onAddEntry = {
                                val updatedList = cookies + newEntry("cookie")
                                actions.onCookiesChanged(updatedList)
                            },
                            onRemoveEntry = { targetIndex ->
                                val updatedList = cookies.toMutableList().apply { removeAt(targetIndex) }
                                actions.onCookiesChanged(updatedList)
                            },
                            modifier = requestKeyValueEditorModifier
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

private fun newEntry(kind: String): KeyValueEntry = KeyValueEntry(
    id = "$kind-${Uuid.random()}",
    key = "",
    value = ""
)
