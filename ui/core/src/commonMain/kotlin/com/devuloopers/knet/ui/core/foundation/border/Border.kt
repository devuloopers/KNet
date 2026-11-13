package com.devuloopers.knet.ui.core.foundation.border

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/** Theme frame edges; split-pane children leave their adjoining vertical edges to the splitter. */
enum class KNetBorderEdges {
    /** Complete rectangular frame for standalone surfaces. */
    ALL,
    /** Top and bottom separators only, without competing vertical frame lines. */
    HORIZONTAL,
}

/** Paints theme-owned frame edges inside content bounds without changing measurement or padding. */
@Composable
fun Modifier.knetBorder(edges: KNetBorderEdges = KNetBorderEdges.ALL): Modifier {
    val color = KNetTheme.colors.border
    val thickness = KNetTheme.dimensions.borderWidth
    if (edges == KNetBorderEdges.ALL) return border(thickness, color)
    return drawWithContent {
        drawContent()
        val pixels = thickness.toPx().coerceAtMost(size.height / 2)
        drawRect(color, size = Size(size.width, pixels))
        drawRect(color, topLeft = Offset(0f, size.height - pixels), size = Size(size.width, pixels))
    }
}
