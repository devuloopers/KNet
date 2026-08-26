package com.devuloopers.knet.ui.core.components.scrollbar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/** Renders interactive desktop scrollbar chrome for finite vertical content. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal actual fun PlatformKNetVerticalScrollbar(scrollState: ScrollState, modifier: Modifier) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(scrollState),
        modifier = modifier.pointerHoverIcon(PointerIcon.Default),
        style = rememberKNetScrollbarStyle(),
    )
}

/** Renders interactive desktop scrollbar chrome for a virtualized list. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal actual fun PlatformKNetVerticalScrollbar(lazyListState: LazyListState, modifier: Modifier) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(lazyListState),
        modifier = modifier.pointerHoverIcon(PointerIcon.Default),
        style = rememberKNetScrollbarStyle(),
    )
}

/** Renders interactive desktop scrollbar chrome for finite horizontal content. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal actual fun PlatformKNetHorizontalScrollbar(scrollState: ScrollState, modifier: Modifier) {
    HorizontalScrollbar(
        adapter = rememberScrollbarAdapter(scrollState),
        modifier = modifier.pointerHoverIcon(PointerIcon.Default),
        style = rememberKNetScrollbarStyle(),
    )
}

/** Creates KNet's desktop scrollbar style from the active design tokens. */
@Composable
private fun rememberKNetScrollbarStyle(): ScrollbarStyle {
    val colors = KNetTheme.colors
    val dimensions = KNetTheme.dimensions
    return remember(colors, dimensions) {
        ScrollbarStyle(
            minimalHeight = 24.dp,
            thickness = dimensions.scrollbarWidth,
            shape = RoundedCornerShape(percent = 50),
            hoverDurationMillis = 150,
            unhoverColor = colors.textMuted.copy(alpha = 0.45f),
            hoverColor = colors.accent.copy(alpha = 0.9f),
        )
    }
}
