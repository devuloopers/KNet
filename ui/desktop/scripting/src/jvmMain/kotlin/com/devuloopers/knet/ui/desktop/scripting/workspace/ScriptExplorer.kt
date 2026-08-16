package com.devuloopers.knet.ui.desktop.scripting.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.devuloopers.knet.ui.desktop.scripting.model.TrafficScript

/**
 * Script explorer showing available scripts.
 */
@Composable
public fun ScriptExplorer(
    scripts: List<TrafficScript>,
    onScriptSelect: (TrafficScript) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    Column(
        modifier = modifier
            .width(180.dp)
            .fillMaxHeight()
            .padding(8.dp)
    ) {
        Text(
            text = "Scripts",
            style = typography.caption.copy(
                color = themeColors.textPrimary,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        LazyColumn {
            items(scripts) { script ->
                Text(
                    text = script.name,
                    style = typography.caption.copy(color = themeColors.textSecondary),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .handCursor()
                        .clickable { onScriptSelect(script) }
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}

