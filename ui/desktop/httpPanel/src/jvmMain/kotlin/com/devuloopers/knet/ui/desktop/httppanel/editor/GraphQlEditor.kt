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
import com.devuloopers.knet.ui.desktop.codeeditor.api.EditorMode
import com.devuloopers.knet.ui.desktop.codeeditor.api.KNetCodeEditor
import com.devuloopers.knet.ui.desktop.httppanel.model.GraphQlState
import com.devuloopers.knet.ui.desktop.httppanel.model.GraphQlSubTab

/**
 * Reusable, standalone GraphQL Body Editor composable supporting structured editing of
 * Query / Mutation document, Variables (JSON), Extensions (JSON), and Operation Name.
 *
 * SRP: Manages GraphQL body presentation sub-tabs and controls independently of generic HTTP body modes.
 * Uses a single stable [KNetCodeEditor] call site driven by [GraphQlSubTab] SSOT to prevent layout flashing.
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
                code = activeSubTab.getPayload(state),
                configuration = CodeEditorConfiguration(
                    mode = EditorMode.Editable,
                    language = activeSubTab.codeLanguage,
                    placeholder = activeSubTab.placeholder
                ),
                actions = CodeEditorActions(
                    onTextChange = { onStateChange(activeSubTab.updatePayload(state, it)) },
                    onPrettify = { onStateChange(activeSubTab.prettify(state)) }
                ),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
