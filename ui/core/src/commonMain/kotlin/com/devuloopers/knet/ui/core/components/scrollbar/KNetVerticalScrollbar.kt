package com.devuloopers.knet.ui.core.components.scrollbar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Theme-aware vertical scrollbar for finite [ScrollState] content.
 *
 * The scrollbar is absent when the viewport already contains all content, so callers do not reserve
 * unnecessary trailing chrome. It overlays content and shares the caller-owned scroll state.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KNetVerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val visible by remember(scrollState) {
        derivedStateOf { shouldShowScrollbar(scrollState.maxValue) }
    }
    if (!visible) return

    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(scrollState),
        modifier = modifier
            .pointerHoverIcon(PointerIcon.Default),
        style = rememberKNetScrollbarStyle(),
    )
}

/**
 * Theme-aware vertical scrollbar for virtualized [LazyListState] content.
 *
 * Visibility follows the list's actual ability to move rather than its item count because viewport and row
 * measurements determine whether a list overflows.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KNetVerticalScrollbar(
    lazyListState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val visible by remember(lazyListState) {
        derivedStateOf {
            shouldShowScrollbar(
                canScrollBackward = lazyListState.canScrollBackward,
                canScrollForward = lazyListState.canScrollForward,
            )
        }
    }
    if (!visible) return

    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(lazyListState),
        modifier = modifier
            .pointerHoverIcon(PointerIcon.Default),
        style = rememberKNetScrollbarStyle(),
    )
}

@Composable
internal fun rememberKNetScrollbarStyle(): ScrollbarStyle {
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

internal fun shouldShowScrollbar(maximumScrollOffset: Int): Boolean = maximumScrollOffset > 0

internal fun shouldShowScrollbar(canScrollBackward: Boolean, canScrollForward: Boolean): Boolean =
    canScrollBackward || canScrollForward
