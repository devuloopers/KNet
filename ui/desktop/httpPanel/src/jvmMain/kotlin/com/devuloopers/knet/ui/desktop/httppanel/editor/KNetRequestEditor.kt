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
 * Cohesive actions parameter object for [KNetRequestEditor].
 */
data class KNetRequestEditorActions(
    val onBodyStateChanged: (BodyState) -> Unit = {},
    val onBodyPayloadChanged: (String) -> Unit = {},
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
 * Unified interactive HTTP request editor composable shared across API Studio payload authoring
 * and Phase 4 Breakpoint in-flight request modification.
 *
 * Renders standardized edge-to-edge sub-tabs, panelHeader key-value editors, body authoring,
 * auth state configuration, and pre/post-request scripting views.
 */
@Composable
fun KNetRequestEditor(
    bodyState: BodyState = BodyState(),
    bodyPayload: String = bodyState.payloadText,
    queryParams: List<Pair<String, String>> = emptyList(),
    headers: List<Pair<String, String>> = emptyList(),
    cookies: List<Pair<String, String>> = emptyList(),
    authState: AuthState = AuthState(),
    preRequestScript: String = "",
    testScript: String = "",
    activeSubTab: InspectorSubTab = InspectorSubTab.BODY,
    activeScriptPhase: ScriptPhase = ScriptPhase.PRE_REQUEST,
    scriptLanguage: ScriptLanguage = ScriptLanguage.JAVASCRIPT,
    actions: KNetRequestEditorActions = KNetRequestEditorActions(),
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val spacing = KNetTheme.spacing

    val paramEntries = remember(queryParams) {
        queryParams.mapIndexed { index, (paramKey, paramValue) ->
            KeyValueEntry("param_$index", paramKey, paramValue)
        }
    }
    val headerEntries = remember(headers) {
        headers.mapIndexed { index, (headerKey, headerValue) ->
            KeyValueEntry("header_$index", headerKey, headerValue)
        }
    }
    val cookieEntries = remember(cookies) {
        cookies.mapIndexed { index, (cookieKey, cookieValue) ->
            KeyValueEntry("cookie_$index", cookieKey, cookieValue)
        }
    }

    val scriptState = remember(preRequestScript, testScript, activeScriptPhase, scriptLanguage) {
        ScriptState(
            preRequestScript = preRequestScript,
            testScript = testScript,
            scriptLanguage = scriptLanguage,
            activePhase = activeScriptPhase
        )
    }

    val activeParamsCount = queryParams.count { it.first.isNotBlank() }
    val activeHeadersCount = headerEntries.count { it.key.isNotBlank() }

    Column(modifier = modifier.fillMaxSize()) {
        // Standardized Sub-Tab Navigation Bar matching Response Inspector
        InspectorSubTabRow(
            tabs = InspectorSubTab.RequestTabs,
            activeTab = activeSubTab,
            onTabSelected = actions.onSubTabSelected,
            headerCount = activeHeadersCount,
            paramCount = activeParamsCount,
            cookieCount = cookies.size,
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider(color = themeColors.border)

        // Sub-Tab Panel Content
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (activeSubTab) {
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

                InspectorSubTab.AUTH -> {
                    AuthEditorView(
                        state = authState,
                        onStateChange = actions.onAuthStateChanged,
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

                InspectorSubTab.BODY -> {
                    BodyEditorView(
                        state = bodyState,
                        onStateChange = { updatedBodyState ->
                            actions.onBodyStateChanged(updatedBodyState)
                            actions.onBodyPayloadChanged(updatedBodyState.payloadText)
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
                                val updatedEntries =
                                    cookieEntries.toMutableList().apply { set(entryIndex, updatedEntry) }
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

                InspectorSubTab.SCRIPTS -> {
                    ScriptEditorView(
                        state = scriptState,
                        onStateChange = { updated ->
                            actions.onPreRequestScriptChanged(updated.preRequestScript)
                            actions.onTestScriptChanged(updated.testScript)
                            actions.onScriptPhaseSelected(updated.activePhase)
                            actions.onScriptLanguageChanged(updated.scriptLanguage)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
