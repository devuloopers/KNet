package com.devuloopers.knet.ui.core.components.scrollbar

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/** Theme-aware horizontal scrollbar, rendered only when its content overflows. */
@Composable
fun KNetHorizontalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val visible by remember(scrollState) { derivedStateOf { scrollState.maxValue > 0 } }
    if (!visible) return
    PlatformKNetHorizontalScrollbar(scrollState = scrollState, modifier = modifier)
}

/** Renders the platform-appropriate horizontal scrollbar chrome. */
@Composable
internal expect fun PlatformKNetHorizontalScrollbar(scrollState: ScrollState, modifier: Modifier)
