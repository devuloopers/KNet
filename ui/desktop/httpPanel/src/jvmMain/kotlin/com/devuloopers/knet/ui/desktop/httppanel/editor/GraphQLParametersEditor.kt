package com.devuloopers.knet.ui.desktop.httppanel.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.components.inspector.KNetInspectorParagraph
import com.devuloopers.knet.ui.core.components.tabs.KNetTab
import com.devuloopers.knet.ui.core.components.tabs.KNetTabRow
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.httppanel.model.GraphQLParameterEditorState
import com.devuloopers.knet.ui.desktop.httppanel.model.GraphQlState

/**
 * Shared source-aware GET authoring surface with inset design-system navigation and editor placement.
 * Raw URL rows retain their own shared request-panel inset alongside decoded GraphQL editing.
 */
@Composable
internal fun GraphQLParametersEditor(
    state: GraphQLParameterEditorState?,
    onChanged: (GraphQlState) -> Unit,
    rawContent: @Composable () -> Unit,
) {
    val spacing = KNetTheme.spacing
    var raw by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        if (state != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                KNetTabRow(Modifier.fillMaxWidth()) {
                    KNetTab("GraphQL", selected = !raw, onClick = { raw = false })
                    KNetTab("Raw parameters", selected = raw, onClick = { raw = true })
                }
                Text(
                    text = "Source: URL parameters",
                    maxLines = 1,
                    softWrap = false,
                    style = KNetTheme.typography.caption,
                    color = KNetTheme.colors.textMuted,
                )
                state.diagnostic?.let {
                    KNetInspectorParagraph(
                        text = it.description,
                        modifier = Modifier.fillMaxWidth(),
                        color = KNetTheme.colors.semantic.warning,
                    )
                }
            }
        }
        if (state != null && !raw) {
            GraphQlEditor(
                state = state.graphQl,
                onStateChange = onChanged,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = spacing.md, end = spacing.md, bottom = spacing.sm),
            )
        } else {
            rawContent()
        }
    }
}
