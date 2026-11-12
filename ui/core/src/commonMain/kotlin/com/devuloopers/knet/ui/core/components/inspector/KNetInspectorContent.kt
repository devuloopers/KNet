package com.devuloopers.knet.ui.core.components.inspector

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.devuloopers.knet.ui.core.components.scrollbar.KNetHorizontalScrollbar
import com.devuloopers.knet.ui.core.components.scrollbar.KNetVerticalScrollbar
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Shared inspector overflow policy: retain the design-system minimum readable width and expose both scrollbars.
 * The feature supplies content only; dimensions, padding and clipping are owned here.
 * @param modifier Viewport supplied by the hosting split pane.
 * @param content Finite metadata and actions rendered in the scrolling inspector column.
 */
@Composable
fun KNetInspectorContent(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()
    val spacing = KNetTheme.spacing
    BoxWithConstraints(modifier.background(KNetTheme.colors.surface)) {
        val contentWidth = maxOf(maxWidth - spacing.md, KNetTheme.dimensions.inspectorMinWidth)
        Box(Modifier.fillMaxSize().padding(end = spacing.md, bottom = spacing.md).horizontalScroll(horizontal)) {
            Column(Modifier.width(contentWidth).fillMaxHeight().verticalScroll(vertical).padding(spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.lg), content = content)
        }
        KNetVerticalScrollbar(vertical, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        KNetHorizontalScrollbar(horizontal, Modifier.align(Alignment.BottomStart).fillMaxWidth())
    }
}

/**
 * Readable explanatory inspector copy wraps within the shared viewport instead of truncating diagnostic reasons.
 * @param text Explanatory copy, never interpreted as markup.
 * @param modifier Parent layout modifier.
 * @param style Typography, normally a theme body or caption style.
 * @param color Semantic text color from the active KNet palette.
 */
@Composable
fun KNetInspectorParagraph(text: String, modifier: Modifier = Modifier,
    style: TextStyle = KNetTheme.typography.bodySmall, color: Color = KNetTheme.colors.textSecondary) {
    Text(text, modifier, style = style.copy(color = color), softWrap = true)
}

/**
 * Inspector actions retain their intrinsic label width and move to another line when the viewport is narrow.
 * @param modifier Layout and accessibility metadata supplied by the caller.
 * @param horizontalAlignment Alignment for each line of actions within the shared viewport.
 * @param content Theme buttons; consumers do not impose feature-specific resize breakpoints.
 */
@Composable
fun KNetInspectorActions(modifier: Modifier = Modifier, horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm, horizontalAlignment),
        verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm), content = content)
}
