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
 * ProblemsView showing warnings and errors found during compilation/static diagnostics analysis.
 */
@Composable
fun ProblemsView(
    diagnostics: List<ScriptDiagnostic>, onDiagnosticSelect: (ScriptDiagnostic) -> Unit, modifier: Modifier = Modifier
) {
    val problems = diagnostics.filter {
        it.severity == ScriptDiagnosticSeverity.ERROR || it.severity == ScriptDiagnosticSeverity.WARNING
    }
    Column(modifier = modifier.padding(8.dp)) {
        Text(
            text = "Problems (${problems.size})",
            color = KNetColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        LazyColumn {
            items(problems) { problem ->
                val color =
                    if (problem.severity == ScriptDiagnosticSeverity.ERROR) KNetColors.ErrorRed else KNetColors.WarningOrange
                Text(
                    text = "Line ${problem.line}: ${problem.message}",
                    color = color,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth().clickable { onDiagnosticSelect(problem) }
                        .padding(vertical = 4.dp))
            }
        }
    }
}
