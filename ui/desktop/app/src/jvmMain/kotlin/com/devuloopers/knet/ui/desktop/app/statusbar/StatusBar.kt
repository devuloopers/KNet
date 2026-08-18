package com.devuloopers.knet.ui.desktop.app.statusbar

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.components.statusbar.KNetStatusBar
import com.devuloopers.knet.ui.core.components.statusbar.StatusItem
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Status bar footer container composable hosting status items.
 *
 * @param modifier Layout modifier.
 */
@Composable
fun StatusBar(
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    KNetStatusBar(modifier = modifier) {
        StatusItem(text = "Proxy Engine: Active (8080)")
        Text(
            text = "SSL/TLS MITM: Ready",
            style = typography.bodySmall.copy(color = themeColors.semantic.success),
            modifier = Modifier.weight(1f)
        )
        StatusItem(text = "Memory: 64MB / 512MB")
    }
}
