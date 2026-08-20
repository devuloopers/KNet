package com.devuloopers.knet.ui.core.components.scrollbar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon

/** Theme-aware horizontal scrollbar, rendered only when its content overflows. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KNetHorizontalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val visible by remember(scrollState) { derivedStateOf { scrollState.maxValue > 0 } }
    if (!visible) return
    HorizontalScrollbar(
        adapter = rememberScrollbarAdapter(scrollState),
        modifier = modifier.pointerHoverIcon(PointerIcon.Default),
        style = rememberKNetScrollbarStyle(),
    )
}
