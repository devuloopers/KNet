package com.devuloopers.knet.ui.desktop.protectedtraffic.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/** Manager shell with compact default-policy controls; evidence and connection actions live in Traffic. */
@Composable
internal fun ProtectedTrafficWorkspace(modifier: Modifier = Modifier, compact: Boolean = false,
    headerActions: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    val spacing = KNetTheme.spacing
    Column(modifier.fillMaxSize().background(KNetTheme.colors.background).padding(spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        if (compact) Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            ManagerTitle(); headerActions()
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.lg)) {
            ManagerTitle(Modifier.weight(1f)); headerActions()
        }
        Box(Modifier.fillMaxWidth().weight(1f)) { content() }
        ManagerText("Saved rules take priority. Changes apply to new connections. View connection decisions in Traffic.", caption = true)
    }
}

@Composable
private fun ManagerTitle(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs)) {
        Text("Protected Traffic", style = KNetTheme.typography.titleLarge.copy(color = KNetTheme.colors.textPrimary),
            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        ManagerText("Manage default TLS handling and saved connection rules.", caption = true)
    }
}
