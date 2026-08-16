package com.devuloopers.knet.ui.desktop.app.toolbar

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.button.KNetIconButton
import com.devuloopers.knet.ui.core.components.input.KNetSearchField
import com.devuloopers.knet.ui.core.components.toolbar.KNetToolbar
import com.devuloopers.knet.ui.core.components.toolbar.ToolbarGroup
import com.devuloopers.knet.ui.core.components.toolbar.ToolbarSeparator
import com.devuloopers.knet.ui.core.components.toolbar.ToolbarSpacer
import com.devuloopers.knet.ui.core.foundation.theme.ThemeMode

/**
 * Compact IDE global toolbar composable adhering to refined button density and surface hierarchy rules.
 */
@Composable
public fun KNetGlobalToolbar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    currentThemeMode: ThemeMode,
    onThemeModeToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    KNetToolbar(modifier = modifier) {
        ToolbarGroup {
            KNetButton(
                onClick = {},
                variant = ButtonVariant.Primary
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "+ New ▾",
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            KNetIconButton(onClick = {}, icon = Icons.Default.Refresh, contentDescription = "Import/Refresh")
            KNetIconButton(onClick = {}, icon = Icons.Default.PlayArrow, contentDescription = "Run Execution")
            KNetIconButton(onClick = {}, icon = Icons.Default.Clear, contentDescription = "Clear Session")
        }

        ToolbarSeparator()

        ToolbarGroup {
            KNetButton(onClick = {}, variant = ButtonVariant.Ghost) {
                Text(
                    text = "∇ Filter",
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        ToolbarSpacer()

        KNetSearchField(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            placeholder = "Search... (Ctrl+K)",
            modifier = Modifier.width(200.dp).padding(horizontal = 4.dp)
        )

        ToolbarGroup {
            KNetIconButton(
                onClick = {},
                icon = Icons.Default.Settings,
                contentDescription = "Settings"
            )
            KNetButton(
                onClick = onThemeModeToggle,
                variant = ButtonVariant.Secondary
            ) {
                Text(
                    text = "Theme: ${currentThemeMode.name} ▾",
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
