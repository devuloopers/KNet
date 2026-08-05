package com.devuloopers.knet.ui.core.components.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

import androidx.compose.ui.text.style.TextOverflow

@Composable
public fun KNetTab(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDirty: Boolean = false,
    onClose: (() -> Unit)? = null
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes

    val backgroundColor = if (selected) themeColors.surface else Color.Transparent
    val textColor = if (selected) themeColors.accent else themeColors.textSecondary

    Row(
        modifier = modifier
            .height(28.dp)
            .clip(shapes.small)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .handCursor()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isDirty) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(shapes.pill)
                    .background(themeColors.accent)
                    .padding(end = 4.dp)
            )
        }
        Text(
            text = title,
            style = typography.labelSmall.copy(color = textColor),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(end = 4.dp)
        )
        if (onClose != null) {
            Icon(
                imageVector = KNetIcons.Close,
                contentDescription = "Close Tab",
                modifier = Modifier
                    .size(12.dp)
                    .clickable(onClick = onClose)
                    .handCursor(),
                tint = themeColors.textMuted
            )
        }
    }
}

@Composable
public fun ClosableTab(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    KNetTab(
        title = title,
        selected = selected,
        onClick = onClick,
        onClose = onClose,
        modifier = modifier
    )
}

@Composable
public fun KNetTabRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val themeColors = KNetTheme.colors
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .background(themeColors.surfaceVariant)
            .horizontalScroll(scrollState)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

@Composable
public fun ScrollableTabRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val themeColors = KNetTheme.colors
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .background(themeColors.surfaceVariant)
            .horizontalScroll(scrollState)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}
