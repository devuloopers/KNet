package com.devuloopers.knet.ui.desktop.httppanel.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.engine.formatter.graphql.GraphQLQuerySynchronizer
import com.devuloopers.knet.ui.core.components.input.InputFieldConfig
import com.devuloopers.knet.ui.core.components.input.KNetTextField
import com.devuloopers.knet.ui.core.components.tabs.KNetTab
import com.devuloopers.knet.ui.core.components.tabs.KNetTabRow
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.codeeditor.api.CodeEditorActions
import com.devuloopers.knet.ui.desktop.codeeditor.api.CodeEditorConfiguration
import com.devuloopers.knet.ui.desktop.codeeditor.api.CodeEditorHeaderConfiguration
import com.devuloopers.knet.ui.desktop.codeeditor.api.CodeEditorState
import com.devuloopers.knet.ui.desktop.codeeditor.api.EditorMode
import com.devuloopers.knet.ui.desktop.codeeditor.api.KNetCodeEditor
import com.devuloopers.knet.ui.desktop.codeeditor.api.rememberCodeEditorState
import com.devuloopers.knet.ui.desktop.httppanel.model.GraphQlState
import com.devuloopers.knet.ui.desktop.httppanel.model.GraphQlSubTab

/**
 * Reusable, standalone GraphQL Body Editor composable supporting structured editing of
 * Query / Mutation document, Variables (JSON), Extensions (JSON), and Operation Name.
 *
 * SRP: Manages GraphQL body presentation sub-tabs and controls independently of generic HTTP body modes.
 * Uses one stable editor session per GraphQL sub-document and a single [KNetCodeEditor] presentation call site.
 * Switching tabs therefore preserves document-local history, caret, and selection without replacing one session's
 * complete text with another logical document.
 *
 * @param state Immutable [GraphQlState] holding active query, variables, operationName, and sub-tab selection.
 * @param onStateChange Callback invoked with updated [GraphQlState] whenever any field changes.
 * @param modifier Optional modifier applied to the root container layout.
 */
@Composable
fun GraphQlEditor(
    state: GraphQlState,
    onStateChange: (GraphQlState) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val spacing = KNetTheme.spacing
    val activeSubTab = state.activeSubTab
    val queryEditorState = rememberCodeEditorState(state.queryText)
    val variablesEditorState = rememberCodeEditorState(state.variablesText)
    val extensionsEditorState = rememberCodeEditorState(state.extensionsText)
    val synchronizedTexts = remember {
        mutableMapOf(
            GraphQlSubTab.QUERY to state.queryText,
            GraphQlSubTab.VARIABLES to state.variablesText,
            GraphQlSubTab.EXTENSIONS to state.extensionsText
        )
    }

    LaunchedEffect(state.queryText) {
        synchronizeGraphQlEditorState(
            subTab = GraphQlSubTab.QUERY,
            externalText = state.queryText,
            editorState = queryEditorState,
            synchronizedTexts = synchronizedTexts
        )
    }
    LaunchedEffect(state.variablesText) {
        synchronizeGraphQlEditorState(
            subTab = GraphQlSubTab.VARIABLES,
            externalText = state.variablesText,
            editorState = variablesEditorState,
            synchronizedTexts = synchronizedTexts
        )
    }
    LaunchedEffect(state.extensionsText) {
        synchronizeGraphQlEditorState(
            subTab = GraphQlSubTab.EXTENSIONS,
            externalText = state.extensionsText,
            editorState = extensionsEditorState,
            synchronizedTexts = synchronizedTexts
        )
    }

    val activeEditorState = when (activeSubTab) {
        GraphQlSubTab.QUERY -> queryEditorState
        GraphQlSubTab.VARIABLES -> variablesEditorState
        GraphQlSubTab.EXTENSIONS -> extensionsEditorState
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(spacing.xs)
    ) {
        // Sub-Tab Navigation Bar & Operation Name Input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Sub-Tab Chips using reusable KNetTabRow & KNetTab
            KNetTabRow(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = spacing.sm)
            ) {
                GraphQlSubTab.entries.forEach { subTab ->
                    KNetTab(
                        title = subTab.label,
                        selected = state.activeSubTab == subTab,
                        onClick = { onStateChange(state.copy(activeSubTab = subTab)) }
                    )
                }
            }

            // Compact Operation Name Input Field
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Op Name:",
                    style = typography.caption.copy(color = themeColors.textMuted, fontSize = 11.sp)
                )
                KNetTextField(
                    value = state.operationName,
                    onValueChange = { newOpName ->
                        val updatedQuery = GraphQLQuerySynchronizer.updateOperationName(state.queryText, newOpName)
                        onStateChange(
                            state.copy(
                                payload = state.payload.copy(
                                    operationName = newOpName,
                                    queryText = updatedQuery,
                                )
                            )
                        )
                    },
                    modifier = Modifier.width(200.dp),
                    config = InputFieldConfig(
                        placeholder = "OperationName",
                        backgroundColor = themeColors.surfaceVariant,
                        borderColor = themeColors.border
                    )
                )
            }
        }

        // Active Sub-Tab Editor Content Area (Single Stable Call Site)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            KNetCodeEditor(
                state = activeEditorState,
                configuration = CodeEditorConfiguration(
                    mode = EditorMode.Editable,
                    language = activeSubTab.codeLanguage,
                    header = CodeEditorHeaderConfiguration(actions = prettifyEditorHeaderActions),
                    placeholder = activeSubTab.placeholder
                ),
                actions = CodeEditorActions(
                    onTextChange = { updatedText ->
                        synchronizedTexts[activeSubTab] = updatedText
                        onStateChange(activeSubTab.updatePayload(state, updatedText))
                    },
                    onCommand = { command ->
                        dispatchPrettifyEditorHeaderAction(command) {
                            onStateChange(activeSubTab.prettify(state))
                        }
                    }
                ),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Applies a controlling GraphQL payload change to its matching editor session without echoing user edits back as
 * external document replacements. External changes include request restoration, operation-name synchronization,
 * and Prettify results.
 */
private fun synchronizeGraphQlEditorState(
    subTab: GraphQlSubTab,
    externalText: String,
    editorState: CodeEditorState,
    synchronizedTexts: MutableMap<GraphQlSubTab, String>
) {
    if (synchronizedTexts[subTab] == externalText) return
    synchronizedTexts[subTab] = externalText
    editorState.replaceFromExternal(externalText)
}
