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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
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
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(themeColors.surface)
            .padding(vertical = 4.dp)
    ) {
        openScripts.forEach { script ->
            val isSelected = script.id == activeScript?.id
            Text(
                text = script.name + if (script.isDirty) "*" else "",
                style = typography.caption.copy(
                    color = if (isSelected) themeColors.accent else themeColors.textSecondary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .handCursor()
                    .clickable { onScriptSelect(script) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

