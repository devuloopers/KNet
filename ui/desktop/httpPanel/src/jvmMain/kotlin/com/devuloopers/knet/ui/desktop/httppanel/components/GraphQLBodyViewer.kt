package com.devuloopers.knet.ui.desktop.httppanel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.engine.formatter.model.BodyFormat
import com.devuloopers.knet.ui.core.components.badge.KNetBadge
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.components.placeholder.KNetEmptyStatePlaceholder
import com.devuloopers.knet.ui.core.components.tabs.KNetTab
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.codeeditor.api.EditorMode
import com.devuloopers.knet.ui.desktop.codeeditor.api.KNetCodeEditor
import com.devuloopers.knet.ui.desktop.codeeditor.model.CodeLanguage

/**
 * Sub-tabs available directly within the GraphQL request body inspector.
 *
 * @property label Standard human-readable tab display name.
 */
enum class GraphQLBodySubTab(val label: String) {
    QUERY("Query"),
    VARIABLES("Variables"),
    EXTENSIONS("Extensions"),
    RAW_JSON("Raw JSON");

    /**
     * Resolves the user-facing title, appending `(0)` when optional sections are empty.
     */
    fun resolveTitle(hasContent: Boolean): String {
        return when (this) {
            VARIABLES -> if (hasContent) label else "$label (0)"
            EXTENSIONS -> if (hasContent) label else "$label (0)"
            else -> label
        }
    }
}

/**
 * Dedicated high-density GraphQL request body viewer composable.
 *
 * Provides sub-tabs for GraphQL Query Document (with syntax highlighting),
 * Variables (JSON), Extensions (JSON), and formatted Raw JSON transport payload.
 *
 * @param format Strongly-typed [BodyFormat.GraphQL] domain model containing parsed query AST, variables, and extensions.
 * @param rawJsonText Raw JSON representation of the GraphQL POST request body.
 * @param modifier Layout modifier applied to the root container.
 */
@Composable
public fun GraphQLBodyViewer(
    format: BodyFormat.GraphQL,
    rawJsonText: String,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val spacing = KNetTheme.spacing

    var activeSubTab by remember { mutableStateOf(GraphQLBodySubTab.QUERY) }

    val hasVariables = format.variablesJson.isNotBlank() && format.variablesJson.trim() != "{}"
    val hasExtensions = format.extensionsJson.isNotBlank() && format.extensionsJson.trim() != "{}"

    Column(modifier = modifier.fillMaxSize()) {
        // Sub-Tab Navigation Bar with Operation Name Badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(themeColors.surface)
                .padding(horizontal = spacing.sm, vertical = spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Horizontal scrollable sub-tabs row
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GraphQLBodySubTab.entries.forEach { subTab ->
                    val isSelected = activeSubTab == subTab
                    val hasContent = when (subTab) {
                        GraphQLBodySubTab.QUERY -> format.queryText.isNotBlank()
                        GraphQLBodySubTab.VARIABLES -> hasVariables
                        GraphQLBodySubTab.EXTENSIONS -> hasExtensions
                        GraphQLBodySubTab.RAW_JSON -> rawJsonText.isNotBlank()
                    }
                    val title = subTab.resolveTitle(hasContent)

                    KNetTab(
                        title = title,
                        selected = isSelected,
                        onClick = { activeSubTab = subTab }
                    )
                }
            }

            // Operation Name Badge (if present)
            val opName = format.operationName
            if (!opName.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    modifier = Modifier.padding(start = spacing.sm)
                ) {
                    Text(
                        text = "Operation:",
                        style = typography.caption.copy(color = themeColors.textMuted),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    KNetBadge(
                        text = opName,
                        containerColor = themeColors.accent.copy(alpha = 0.15f),
                        contentColor = themeColors.accent
                    )
                }
            }
        }

        HorizontalDivider(
            color = themeColors.border,
            modifier = Modifier.height(1.dp)
        )

        // Sub-Tab Content View
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (activeSubTab) {
                GraphQLBodySubTab.QUERY -> {
                    if (format.queryText.isBlank()) {
                        KNetEmptyStatePlaceholder(
                            title = "No GraphQL Query",
                            subtitle = "This GraphQL payload does not define a query document.",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        KNetCodeEditor(
                            code = format.queryText,
                            language = CodeLanguage.GRAPHQL,
                            mode = EditorMode.ReadOnly,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                GraphQLBodySubTab.VARIABLES -> {
                    if (!hasVariables) {
                        KNetEmptyStatePlaceholder(
                            title = "No Variables",
                            subtitle = "This GraphQL operation has no query variables.",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        KNetCodeEditor(
                            code = format.variablesJson,
                            language = CodeLanguage.JSON,
                            mode = EditorMode.ReadOnly,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                GraphQLBodySubTab.EXTENSIONS -> {
                    if (!hasExtensions) {
                        KNetEmptyStatePlaceholder(
                            title = "No Extensions",
                            subtitle = "This GraphQL operation does not include protocol extensions.",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        KNetCodeEditor(
                            code = format.extensionsJson,
                            language = CodeLanguage.JSON,
                            mode = EditorMode.ReadOnly,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                GraphQLBodySubTab.RAW_JSON -> {
                    if (rawJsonText.isBlank()) {
                        KNetEmptyStatePlaceholder(
                            title = "No Raw Body",
                            subtitle = "No raw JSON payload available for this GraphQL request.",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        KNetCodeEditor(
                            code = rawJsonText,
                            language = CodeLanguage.JSON,
                            mode = EditorMode.ReadOnly,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
