package com.devuloopers.knet.ui.core.components.scrollbar

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Theme-aware vertical scrollbar for finite [ScrollState] content.
 *
 * The scrollbar is absent when the viewport already contains all content, so callers do not reserve
 * unnecessary trailing chrome. It overlays content and shares the caller-owned scroll state.
 */
@Composable
fun KNetVerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val visible by remember(scrollState) {
        derivedStateOf {
            shouldShowScrollbar(
                viewportSize = scrollState.viewportSize,
                maximumScrollOffset = scrollState.maxValue,
            )
        }
    }
    if (!visible) return

    PlatformKNetVerticalScrollbar(scrollState = scrollState, modifier = modifier)
}

/**
 * Theme-aware vertical scrollbar for virtualized [LazyListState] content.
 *
 * Visibility follows the list's actual ability to move rather than its item count because viewport and row
 * measurements determine whether a list overflows.
 */
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

    PlatformKNetVerticalScrollbar(lazyListState = lazyListState, modifier = modifier)
}

/**
 * Theme-owned content clearance for a virtualized list's visible scrollbar, including breathing space.
 * Header and row viewports consume the same value so clipped content cannot pass beneath the pinned chrome.
 * No gutter is reserved until measured list overflow proves that the scrollbar is needed.
 */
@Composable
fun rememberVerticalScrollbarGutter(lazyListState: LazyListState): Dp {
    val visible by remember(lazyListState) {
        derivedStateOf { shouldShowScrollbar(lazyListState.canScrollBackward, lazyListState.canScrollForward) }
    }
    return if (visible) KNetTheme.dimensions.scrollbarWidth + KNetTheme.spacing.sm else 0.dp
}

/** Renders the platform-appropriate finite vertical scrollbar chrome. */
@Composable
internal expect fun PlatformKNetVerticalScrollbar(scrollState: ScrollState, modifier: Modifier)

/** Renders the platform-appropriate virtualized vertical scrollbar chrome. */
@Composable
internal expect fun PlatformKNetVerticalScrollbar(lazyListState: LazyListState, modifier: Modifier)

internal fun shouldShowScrollbar(
    viewportSize: Int,
    maximumScrollOffset: Int,
): Boolean = viewportSize > 0 && maximumScrollOffset > 0

internal fun shouldShowScrollbar(canScrollBackward: Boolean, canScrollForward: Boolean): Boolean =
    canScrollBackward || canScrollForward
