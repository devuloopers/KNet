package com.devuloopers.knet.ui.desktop.scripting.diagnostics

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.desktop.scripting.model.ScriptDiagnostic

/**
 * DiagnosticsView container combining both compiling errors/problems list and suggestions.
 */
@Composable
fun DiagnosticsView(
    diagnostics: List<ScriptDiagnostic>,
    onDiagnosticSelect: (ScriptDiagnostic) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth()) {
        ProblemsView(
            diagnostics = diagnostics,
            onDiagnosticSelect = onDiagnosticSelect,
            modifier = Modifier.weight(1f)
        )
        SuggestionsView(
            diagnostics = diagnostics,
            onSuggestionSelect = onDiagnosticSelect,
            modifier = Modifier.weight(1f)
        )
    }
}
