package com.devuloopers.knet.ui.core.components.inspector

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.components.scrollbar.KNetHorizontalScrollbar
import com.devuloopers.knet.ui.core.components.scrollbar.KNetVerticalScrollbar
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Shared list/detail viewport for framed protocols. Narrow inspectors scroll horizontally;
 * neither pane collapses into unreadable text. Slots receive finite width and height.
 * [contentIdentity] resets horizontal scrolling without replacing the list/detail composition.
 */
@Composable
fun KNetInspectorMessageLayout(modifier: Modifier = Modifier, contentIdentity: String? = null, list: @Composable () -> Unit,
    detail: @Composable ColumnScope.() -> Unit) {
    val horizontal = rememberInspectorScrollState(contentIdentity)
    val dimensions = KNetTheme.dimensions
    BoxWithConstraints(modifier) {
        val width = maxOf(maxWidth, dimensions.inspectorMessageListWidth + dimensions.inspectorMessageDetailMinWidth)
        Column {
            Row(Modifier.weight(1f).horizontalScroll(horizontal).width(width)) {
                Box(Modifier.width(dimensions.inspectorMessageListWidth).fillMaxHeight()) { list() }
                Box(Modifier.width(dimensions.dividerThickness).fillMaxHeight().background(KNetTheme.colors.border))
                Column(Modifier.weight(1f).fillMaxHeight(), content = detail)
            }
            KNetHorizontalScrollbar(horizontal)
        }
    }
}

/** Bounds metadata independently; [contentIdentity] resets its scroll offset without remounting the body viewer. */
@Composable
fun KNetInspectorMessageMetadata(modifier: Modifier = Modifier, contentIdentity: String? = null,
    content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberInspectorScrollState(contentIdentity)
    Box(modifier.heightIn(max = KNetTheme.dimensions.inspectorMessageMetadataMaxHeight)) {
        Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(KNetTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm), content = content)
        KNetVerticalScrollbar(scroll, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}
