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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.theme.KNetColors
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
    val suggestions = diagnostics.filter { it.severity == ScriptDiagnosticSeverity.SUGGESTION }
    Column(modifier = modifier.padding(8.dp)) {
        Text(
            text = "Suggestions (${suggestions.size})",
            color = KNetColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        LazyColumn {
            items(suggestions) { suggestion ->
                Text(
                    text = "Line ${suggestion.line}: ${suggestion.message}",
                    color = KNetColors.ActiveBlue,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSuggestionSelect(suggestion) }
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}

val SuggestionsHeight: androidx.compose.ui.unit.Dp = 100.dp
