package com.devuloopers.knet.ui.core.components.chip

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/** Compact selectable chip; omit [onClick] for a passive label. */
@Composable
fun KNetChip(
    text: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes

    val containerColor = if (selected) themeColors.accent else themeColors.surfaceVariant
    val textColor = if (selected) themeColors.surface else themeColors.textPrimary

    val interactionModifier = if (onClick != null) {
        Modifier
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .handCursor()
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .clip(shapes.pill)
            .background(containerColor)
            .then(interactionModifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = typography.labelSmall.copy(color = textColor),
            maxLines = 1,
            softWrap = false
        )
    }
}
