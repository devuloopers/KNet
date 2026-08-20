package com.devuloopers.knet.ui.core.components.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.button.KNetIconButton
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Selectable workspace tab with optional dirty and close affordances.
 *
 * @param title Visible tab title.
 * @param selected Whether this tab is active.
 * @param onClick Selects the tab.
 * @param modifier Modifier applied to the tab.
 * @param isDirty Whether unsaved state is indicated.
 * @param onClose Optional close action.
 */
@Composable
fun KNetTab(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDirty: Boolean = false,
    onClose: (() -> Unit)? = null
) {
    val colors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes
    val backgroundColor = if (selected) colors.surface else Color.Transparent
    val textColor = if (selected) colors.accent else colors.textSecondary

    Row(
        modifier = modifier
            .height(28.dp)
            .clip(shapes.small)
            .background(backgroundColor)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .handCursor()
            .padding(start = 8.dp, end = if (onClose == null) 8.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isDirty) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(shapes.pill)
                    .background(colors.accent)
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = title,
            style = typography.labelSmall.copy(color = textColor),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 200.dp)
        )
        if (onClose != null) {
            KNetIconButton(
                icon = KNetIcons.Close,
                contentDescription = "Close $title tab",
                onClick = onClose,
                size = 24.dp,
                iconSize = 12.dp,
                tint = colors.textMuted
            )
        }
    }
}

/** Horizontally scrollable single-selection tab container. */
@Composable
fun KNetTabRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = KNetTheme.colors
    val shapes = KNetTheme.shapes
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .selectableGroup()
            .clip(shapes.medium)
            .background(colors.surfaceVariant, shapes.medium)
            .horizontalScroll(scrollState)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}
