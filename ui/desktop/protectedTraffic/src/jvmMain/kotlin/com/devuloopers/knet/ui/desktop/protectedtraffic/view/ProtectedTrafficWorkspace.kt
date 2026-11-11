package com.devuloopers.knet.ui.desktop.protectedtraffic.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/** Policy settings stay here; connection evidence and per-destination actions live in Traffic. */
@Composable
internal fun ProtectedTrafficWorkspace(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = KNetTheme.colors
    val spacing = KNetTheme.spacing
    Column(modifier.fillMaxSize().background(colors.background).padding(spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        Text("Protected Traffic", style = KNetTheme.typography.display.copy(color = colors.textPrimary),
            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        Text("Manage default TLS handling and saved connection rules.",
            style = KNetTheme.typography.bodyMedium.copy(color = colors.textSecondary),
            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        Box(Modifier.fillMaxWidth().weight(1f)) { content() }
        Text("Changes apply to new connections. View connection decisions in Traffic.",
            style = KNetTheme.typography.caption.copy(color = colors.textMuted), maxLines = 1,
            softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}
