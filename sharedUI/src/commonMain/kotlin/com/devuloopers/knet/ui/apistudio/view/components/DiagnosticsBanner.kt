package com.devuloopers.knet.ui.apistudio.view.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.apistudio.scriptanalyzer.model.ScriptDiagnostic
import com.devuloopers.knet.ui.apistudio.scriptanalyzer.model.ScriptQuickFix

/**
 * Modern, non-intrusive Compose banner displayed above script editors to present real-time IDE diagnostics and 1-click Quick Fix refactorings.
 *
 * @param diagnostic Target diagnostic to display.
 * @param onApplyQuickFix Callback triggered when user clicks a Quick Fix action.
 * @param modifier Compose Modifier.
 */
@Composable
fun DiagnosticsBanner(
    diagnostic: ScriptDiagnostic,
    onApplyQuickFix: (ScriptQuickFix) -> Unit,
    modifier: Modifier = Modifier
) {
    val bannerBg = Color(0xFF1E2530)
    val bannerBorder = Color(0xFFE5A50A)
    val textPrimary = Color(0xFFF0F4F8)
    val accentYellow = Color(0xFFFFD54F)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bannerBg, shape = RoundedCornerShape(8.dp))
            .border(1.dp, bannerBorder.copy(alpha = 0.6f), shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lightbulb,
                contentDescription = "Diagnostic Hint",
                tint = accentYellow,
                modifier = Modifier.size(18.dp)
            )

            Text(
                text = diagnostic.message,
                color = textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        if (diagnostic.quickFixes.isNotEmpty()) {
            val quickFix = diagnostic.quickFixes.first()
            Button(
                onClick = { onApplyQuickFix(quickFix) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2563EB),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = quickFix.title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
