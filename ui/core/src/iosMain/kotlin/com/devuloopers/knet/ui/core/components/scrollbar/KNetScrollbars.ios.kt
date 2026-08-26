package com.devuloopers.knet.ui.core.components.scrollbar

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Uses iOS touch-native scrolling without adding desktop scrollbar chrome. */
@Composable
internal actual fun PlatformKNetVerticalScrollbar(scrollState: ScrollState, modifier: Modifier) = Unit

/** Uses iOS touch-native lazy-list scrolling without adding desktop scrollbar chrome. */
@Composable
internal actual fun PlatformKNetVerticalScrollbar(lazyListState: LazyListState, modifier: Modifier) = Unit

/** Uses iOS touch-native horizontal scrolling without adding desktop scrollbar chrome. */
@Composable
internal actual fun PlatformKNetHorizontalScrollbar(scrollState: ScrollState, modifier: Modifier) = Unit
