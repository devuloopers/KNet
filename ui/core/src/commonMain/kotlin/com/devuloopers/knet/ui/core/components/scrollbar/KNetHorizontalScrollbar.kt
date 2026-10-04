package com.devuloopers.knet.ui.core.components.scrollbar

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Theme-aware horizontal scrollbar rendered only when its content overflows.
 *
 * The shared control reserves one small spacing token above its track so horizontally scrollable content never
 * touches the scrollbar. Callers should place it directly after the content that owns [scrollState].
 *
 * @param scrollState State shared with the horizontally scrollable content.
 * @param modifier Modifier applied to the complete spacing-and-scrollbar slot.
 */
@Composable
fun KNetHorizontalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val visible by remember(scrollState) { derivedStateOf { scrollState.maxValue > 0 } }
    if (!visible) return
    Column(modifier = modifier) {
        Spacer(modifier = Modifier.height(KNetTheme.spacing.sm))
        PlatformKNetHorizontalScrollbar(
            scrollState = scrollState,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Renders the platform-appropriate horizontal scrollbar chrome. */
@Composable
internal expect fun PlatformKNetHorizontalScrollbar(scrollState: ScrollState, modifier: Modifier)
