package com.devuloopers.knet.ui.desktop.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Bottom action footer bar for SettingsScreen displaying instant auto-save status.
 */
@Composable
fun SettingsFooterBar(
    message: String?,
    onResetDefaults: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(themeColors.surface)
            .border(1.dp, themeColors.border)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (message != null) {
                Text(
                    text = "✓ $message",
                    style = typography.bodySmall.copy(color = Color(0xFF10B981)),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        KNetButton(
            onClick = onResetDefaults,
            variant = ButtonVariant.Ghost
        ) {
            Text(
                text = "Reset Defaults",
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
