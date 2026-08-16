package com.devuloopers.knet.ui.desktop.scripting.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

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
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        KNetButton(
            onClick = onToggleAutoScroll,
            variant = if (autoScroll) ButtonVariant.Primary else ButtonVariant.Secondary
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Auto Scroll",
                tint = if (autoScroll) themeColors.background else themeColors.textPrimary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Auto-scroll",
                style = typography.caption,
                maxLines = 1,
                softWrap = false
            )
        }

        KNetButton(
            onClick = onClear,
            variant = ButtonVariant.Secondary
        ) {
            Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = "Clear",
                tint = themeColors.textPrimary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Clear",
                style = typography.caption,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

public val ToolbarHeight: Dp = 32.dp
