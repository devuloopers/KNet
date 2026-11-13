package com.devuloopers.knet.ui.desktop.traffic.table

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Paints the selected capture's complete frame inside the visible row viewport.
 *
 * Install before horizontal scrolling and after scrollbar clearance. The cells and selected background
 * still scroll normally, while both vertical frame edges stay visible. Drawing reads lazy layout directly
 * so vertical scrolling moves the frame without per-row recomposition or an input-blocking overlay.
 *
 * @param listState Measured row positions, relative to this viewport's top edge.
 * @param selectedId Canonical selected capture key, or null to omit the frame.
 */
@Composable
internal fun Modifier.trafficSelectionOutline(listState: LazyListState, selectedId: String?): Modifier {
    val color = KNetTheme.colors.accent
    val thickness = KNetTheme.dimensions.borderWidth * 2
    return drawWithContent {
        drawContent()
        if (selectedId == null) return@drawWithContent
        val row = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == selectedId }
            ?: return@drawWithContent
        val stroke = thickness.toPx()
        if (size.width <= stroke || row.size <= stroke) return@drawWithContent
        // Clip partially visible rows at the list bounds; never paint into header/footer or scrollbar chrome.
        clipRect {
            drawRect(
                color = color,
                topLeft = Offset(stroke / 2, row.offset + stroke / 2),
                size = Size(size.width - stroke, row.size - stroke),
                style = Stroke(stroke),
            )
        }
    }
}
