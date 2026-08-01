package com.devuloopers.knet.ui.desktop.scripting.console

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.theme.KNetColors

/**
 * ConsoleToolbar provides controls for console actions.
 */
@Composable
public fun ConsoleToolbar(
    autoScroll: Boolean,
    onToggleAutoScroll: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Button(
            onClick = onToggleAutoScroll,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (autoScroll) KNetColors.ActiveBlue else KNetColors.SurfaceDark
            )
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Auto Scroll",
                tint = KNetColors.TextPrimary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "Auto-scroll", fontSize = 11.sp, color = KNetColors.TextPrimary)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onClear,
            colors = ButtonDefaults.buttonColors(containerColor = KNetColors.SurfaceDark)
        ) {
            Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = "Clear",
                tint = KNetColors.TextPrimary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "Clear", fontSize = 11.sp, color = KNetColors.TextPrimary)
        }
    }
}
 public val ToolbarHeight: androidx.compose.ui.unit.Dp = 32.dp
