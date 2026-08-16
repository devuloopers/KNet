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
 * Serves as the Single Source of Truth (SSOT) for sub-tab metadata, syntax highlighting,
 * payload extraction, and empty state definitions.
 *
 * @property label Standard human-readable tab display name.
 * @property codeLanguage Strongly-typed [CodeLanguage] passed to [KNetCodeEditor] for syntax highlighting.
 */
enum class GraphQLBodySubTab(val label: String, val codeLanguage: CodeLanguage) {
    QUERY("Query", CodeLanguage.GRAPHQL),
    VARIABLES("Variables", CodeLanguage.JSON),
    EXTENSIONS("Extensions", CodeLanguage.JSON),
    RAW_JSON("Raw JSON", CodeLanguage.JSON);

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

    /**
     * Extracts the string payload for this sub-tab from the given [BodyFormat.GraphQL] and raw transport string.
     */
    fun getPayload(format: BodyFormat.GraphQL, rawJsonText: String): String = when (this) {
        QUERY -> format.queryText
        VARIABLES -> format.variablesJson
        EXTENSIONS -> format.extensionsJson
        RAW_JSON -> rawJsonText
    }

    /**
     * Returns the empty state placeholder metadata if the section contains no payload.
     */
    fun getEmptyState(format: BodyFormat.GraphQL, rawJsonText: String): Pair<String, String>? = when (this) {
        QUERY -> if (format.queryText.isBlank()) {
            "No GraphQL Query" to "This GraphQL payload does not define a query document."
        } else null
        VARIABLES -> {
            val hasVars = format.variablesJson.isNotBlank() && format.variablesJson.trim() != "{}"
            if (!hasVars) "No Variables" to "This GraphQL operation has no query variables." else null
        }
        EXTENSIONS -> {
            val hasExt = format.extensionsJson.isNotBlank() && format.extensionsJson.trim() != "{}"
            if (!hasExt) "No Extensions" to "This GraphQL operation does not include protocol extensions." else null
        }
        RAW_JSON -> if (rawJsonText.isBlank()) {
            "No Raw Body" to "No raw JSON payload available for this GraphQL request."
        } else null
    }
}

/**
 * Dedicated high-density GraphQL request body viewer composable.
 *
 * Provides sub-tabs for GraphQL Query Document (with syntax highlighting),
 * Variables (JSON), Extensions (JSON), and formatted Raw JSON transport payload.
 * Uses a single stable [KNetCodeEditor] call site to prevent layout flashing across sub-tabs.
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
                modifier = Modifier
                    .weight(1f, fill = false)
                    .horizontalScroll(rememberScrollState()),
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

        // Sub-Tab Content View (Single Stable Call Site)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val emptyState = activeSubTab.getEmptyState(format, rawJsonText)
            if (emptyState != null) {
                KNetEmptyStatePlaceholder(
                    title = emptyState.first,
                    subtitle = emptyState.second,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                KNetCodeEditor(
                    code = activeSubTab.getPayload(format, rawJsonText),
                    language = activeSubTab.codeLanguage,
                    mode = EditorMode.ReadOnly,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
