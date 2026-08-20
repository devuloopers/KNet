package com.devuloopers.knet.ui.desktop.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.components.badge.KNetBadge
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/** Displays the current operating-system trust state using semantic theme colors. */
@Composable
fun StatusBadge(
    isTrusted: Boolean,
    modifier: Modifier = Modifier,
) {
    val semantic = KNetTheme.colors.semantic
    KNetBadge(
        text = if (isTrusted) "TRUSTED IN OS" else "NOT INSTALLED",
        modifier = modifier,
        containerColor = if (isTrusted) semantic.successContainer else semantic.warningContainer,
        contentColor = if (isTrusted) semantic.success else semantic.warning,
    )
}
