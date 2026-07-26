package com.devuloopers.knet.highlighter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.theme.KNetColors

/**
 * Reusable badge rendered when a block of code (JSON, HTML, XML, JS) is collapsed in the code viewer.
 *
 * @param closingSymbol Optional closing bracket or tag to render after the badge (e.g. "}", "]").
 * @param onToggleFold Callback invoked when the user clicks the badge to expand the block.
 */
@Composable
fun CollapsedBadge(
    closingSymbol: String,
    onToggleFold: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .background(KNetColors.ActiveBlue.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
                .border(1.dp, KNetColors.ActiveBlue.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                .clickable { onToggleFold() }
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Text(
                text = "...",
                color = KNetColors.ActiveBlue,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
        if (closingSymbol.isNotEmpty()) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = closingSymbol.trim(),
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }
    }
}
