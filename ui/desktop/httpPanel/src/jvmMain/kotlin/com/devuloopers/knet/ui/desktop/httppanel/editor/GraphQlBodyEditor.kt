package com.devuloopers.knet.ui.desktop.httppanel.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.engine.formatter.formatters.GraphQLBodyFormatter
import com.devuloopers.knet.ui.core.components.input.InputFieldConfig
import com.devuloopers.knet.ui.core.components.input.KNetTextField
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.codeeditor.api.EditorMode
import com.devuloopers.knet.ui.desktop.codeeditor.api.KNetCodeEditor
import com.devuloopers.knet.ui.desktop.httppanel.model.GraphQlState
import com.devuloopers.knet.ui.desktop.httppanel.model.GraphQlSubTab

/**
 * Reusable, standalone GraphQL Body Editor composable supporting structured editing of
 * Query / Mutation document, Variables (JSON), Extensions (JSON), and Operation Name.
 *
 * SRP: Manages GraphQL body presentation sub-tabs and controls independently of generic HTTP body modes.
 * Reuses central design system tokens, [KNetCodeEditor], and icon assets.
 *
 * @param state Immutable [GraphQlState] holding active query, variables, operationName, and sub-tab selection.
 * @param onStateChange Callback invoked with updated [GraphQlState] whenever any field changes.
 * @param modifier Optional modifier applied to the root container layout.
 */
@Composable
fun GraphQlBodyEditor(
    state: GraphQlState,
    onStateChange: (GraphQlState) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val spacing = KNetTheme.spacing

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
            // Sub-Tab Chips
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                GraphQlSubTab.entries.forEach { subTab ->
                    val isSelected = state.activeSubTab == subTab
                    val chipBackground = if (isSelected) themeColors.accent.copy(alpha = 0.15f) else Color.Transparent
                    val chipBorder = if (isSelected) themeColors.accent.copy(alpha = 0.5f) else themeColors.border
                    val textColor = if (isSelected) themeColors.accent else themeColors.textMuted

                    Box(
                        modifier = Modifier
                            .background(chipBackground, RoundedCornerShape(4.dp))
                            .border(1.dp, chipBorder, RoundedCornerShape(4.dp))
                            .handCursor()
                            .clickable { onStateChange(state.copy(activeSubTab = subTab)) }
                            .padding(horizontal = spacing.sm, vertical = spacing.xxs),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = subTab.label,
                            style = typography.caption.copy(
                                color = textColor,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    }
                }
            }

            // Operation Name Input
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                Text(
                    text = "Op Name:",
                    style = typography.caption.copy(color = themeColors.textMuted, fontSize = 11.sp)
                )
                KNetTextField(
                    value = state.operationName,
                    onValueChange = { onStateChange(state.copy(operationName = it)) },
                    modifier = Modifier.width(200.dp),
                    config = InputFieldConfig(
                        placeholder = "OperationName",
                        backgroundColor = themeColors.surfaceVariant,
                        borderColor = themeColors.border
                    )
                )
            }
        }

        // Active Sub-Tab Editor Content Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (state.activeSubTab) {
                GraphQlSubTab.QUERY -> {
                    KNetCodeEditor(
                        code = state.queryText,
                        mode = EditorMode.Editable(
                            onCodeChange = { onStateChange(state.copy(queryText = it)) },
                            onPrettify = {
                                val formatter = GraphQLBodyFormatter()
                                onStateChange(state.copy(queryText = formatter.formatQuery(state.queryText)))
                            },
                            placeholder = "# Enter GraphQL Query or Mutation...\nquery GetUser {\n  user(id: 1) {\n    name\n    email\n  }\n}"
                        ),
                        languageHint = "graphql",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                GraphQlSubTab.VARIABLES -> {
                    KNetCodeEditor(
                        code = state.variablesText,
                        mode = EditorMode.Editable(
                            onCodeChange = { onStateChange(state.copy(variablesText = it)) },
                            onPrettify = {
                                onStateChange(state.copy(variablesText = formatJsonOrOriginal(state.variablesText)))
                            },
                            placeholder = "// Enter GraphQL variables as JSON...\n{\n  \"id\": \"123\"\n}"
                        ),
                        languageHint = "json",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                GraphQlSubTab.EXTENSIONS -> {
                    KNetCodeEditor(
                        code = state.extensionsText,
                        mode = EditorMode.Editable(
                            onCodeChange = { onStateChange(state.copy(extensionsText = it)) },
                            onPrettify = {
                                onStateChange(state.copy(extensionsText = formatJsonOrOriginal(state.extensionsText)))
                            },
                            placeholder = "// Enter GraphQL extensions metadata as JSON...\n{\n  \"clientLibrary\": {\n    \"name\": \"apollo-kotlin\",\n    \"version\": \"5.0.0\"\n  }\n}"
                        ),
                        languageHint = "json",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

private fun formatJsonOrOriginal(jsonStr: String): String {
    val trimmed = jsonStr.trim()
    if (trimmed.isEmpty()) return GraphQlState.DEFAULT_JSON_OBJECT_PLACEHOLDER
    return try {
        val json = kotlinx.serialization.json.Json { prettyPrint = true; isLenient = true; ignoreUnknownKeys = true }
        val element = json.parseToJsonElement(trimmed)
        if (element is kotlinx.serialization.json.JsonObject && element.isEmpty()) {
            GraphQlState.DEFAULT_JSON_OBJECT_PLACEHOLDER
        } else {
            json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), element)
        }
    } catch (_: Exception) {
        trimmed
    }
}
