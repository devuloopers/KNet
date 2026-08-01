package com.devuloopers.knet.ui.desktop.scripting.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.desktop.scripting.model.TrafficScript

/**
 * ScriptTabs horizontal layout display for open scripting tab items.
 */
@Composable
public fun ScriptTabs(
    openScripts: List<TrafficScript>,
    activeScript: TrafficScript?,
    onScriptSelect: (TrafficScript) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(KNetColors.BackgroundDark)
            .padding(vertical = 4.dp)
    ) {
        openScripts.forEach { script ->
            val isSelected = script.id == activeScript?.id
            Text(
                text = script.name + if (script.isDirty) "*" else "",
                color = if (isSelected) KNetColors.ActiveBlue else KNetColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clickable { onScriptSelect(script) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
