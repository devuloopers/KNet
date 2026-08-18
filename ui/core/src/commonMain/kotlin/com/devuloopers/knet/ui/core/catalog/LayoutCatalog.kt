package com.devuloopers.knet.ui.core.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.panel.KNetPanel
import com.devuloopers.knet.ui.core.components.split.HorizontalSplitPane
import com.devuloopers.knet.ui.core.components.toolbar.KNetToolbar
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

@Composable
fun LayoutCatalog() {
    val typography = KNetTheme.typography
    val colors = KNetTheme.colors

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Toolbar Primitive (32dp)", style = typography.titleMedium, color = colors.textPrimary)
        KNetToolbar(modifier = Modifier.padding(vertical = 8.dp)) {
            Text("Action 1", style = typography.bodySmall, color = colors.textPrimary)
        }

        Text("Panel Container", style = typography.titleMedium, color = colors.textPrimary, modifier = Modifier.padding(top = 16.dp))
        KNetPanel(title = "Inspector Panel", modifier = Modifier.padding(vertical = 8.dp)) {
            Text("Panel Content Area", style = typography.bodyMedium, color = colors.textPrimary)
        }

        Text("Horizontal Split Pane", style = typography.titleMedium, color = colors.textPrimary, modifier = Modifier.padding(top = 16.dp))
        HorizontalSplitPane(
            firstPane = { mod -> Text("Left Pane", modifier = mod, color = colors.textPrimary) },
            secondPane = { mod -> Text("Right Pane", modifier = mod, color = colors.textSecondary) },
            modifier = Modifier.height(100.dp).padding(vertical = 8.dp)
        )
    }
}
