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
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.scripting.model.ScriptDiagnostic
import com.devuloopers.knet.ui.desktop.scripting.model.ScriptDiagnosticSeverity

/**
 * ProblemsView showing warnings and errors found during compilation/static diagnostics analysis.
 */
@Composable
fun ProblemsView(
    diagnostics: List<ScriptDiagnostic>,
    onDiagnosticSelect: (ScriptDiagnostic) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    val problems = diagnostics.filter {
        it.severity == ScriptDiagnosticSeverity.ERROR || it.severity == ScriptDiagnosticSeverity.WARNING
    }
    Column(modifier = modifier.padding(8.dp)) {
        Text(
            text = "Problems (${problems.size})",
            style = typography.caption.copy(
                color = themeColors.textPrimary,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        LazyColumn {
            items(problems) { problem ->
                val color = if (problem.severity == ScriptDiagnosticSeverity.ERROR) {
                    themeColors.semantic.error
                } else {
                    themeColors.semantic.warning
                }
                Text(
                    text = "Line ${problem.line}: ${problem.message}",
                    style = typography.caption.copy(color = color),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .handCursor()
                        .clickable { onDiagnosticSelect(problem) }
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}

