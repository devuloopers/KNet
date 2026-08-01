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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.theme.KNetColors
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
    Column(
        modifier = modifier
            .width(180.dp)
            .fillMaxHeight()
            .padding(8.dp)
    ) {
        Text(
            text = "Scripts",
            color = KNetColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        LazyColumn {
            items(scripts) { script ->
                Text(
                    text = script.name,
                    color = KNetColors.TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onScriptSelect(script) }
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}
