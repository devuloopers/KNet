package com.devuloopers.knet.ui.desktop.scripting.diagnostics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.scripting.model.ScriptDiagnostic
import com.devuloopers.knet.ui.desktop.scripting.model.ScriptDiagnosticSeverity

/**
 * SuggestionsView displays performance guidelines, code optimizations, and diagnostics recommendations.
 */
@Composable
fun SuggestionsView(
    diagnostics: List<ScriptDiagnostic>,
    onSuggestionSelect: (ScriptDiagnostic) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    val suggestions = diagnostics.filter { it.severity == ScriptDiagnosticSeverity.SUGGESTION }
    Column(modifier = modifier.padding(8.dp)) {
        Text(
            text = "Suggestions (${suggestions.size})",
            style = typography.caption.copy(
                color = themeColors.textPrimary,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        LazyColumn {
            items(suggestions) { suggestion ->
                Text(
                    text = "Line ${suggestion.line}: ${suggestion.message}",
                    style = typography.caption.copy(color = themeColors.accent),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .handCursor()
                        .clickable { onSuggestionSelect(suggestion) }
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}

val SuggestionsHeight: Dp = 100.dp

