package com.devuloopers.knet.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.theme.KNetColors

/**
 * A nested border frame designed to wrap sub-components inside parent dashboard workspaces.
 *
 * It provides separate styled tabs/toggles header strips and internal 5.dp spacing slots.
 * Supports optional resizing callback handles on borders and corners.
 *
 * @param headerContent The composable header strip containing tabs, search inputs, or toggles.
 * @param modifier Resizing constraints passed from parent grids.
 * @param resizeLeft Optional callback for left border drag delta.
 * @param resizeRight Optional callback for right border drag delta.
 * @param resizeTop Optional callback for top border drag delta.
 * @param resizeBottom Optional callback for bottom border drag delta.
 * @param content The rendering block slot for the inner widget details.
 */
@Composable
fun SubFrame(
    headerContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    resizeLeft: ((Dp) -> Unit)? = null,
    resizeRight: ((Dp) -> Unit)? = null,
    resizeTop: ((Dp) -> Unit)? = null,
    resizeBottom: ((Dp) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val handleThickness = 4.dp
    var activeDragCursor by remember { mutableStateOf<java.awt.Cursor?>(null) }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(KNetColors.SurfaceDark, RoundedCornerShape(6.dp))
                .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(6.dp))
        ) {
            // Sub-Frame Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(KNetColors.BackgroundDark, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                headerContent()
            }

            // Sub-Frame Content Box (Consistently spaced 5.dp internally)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(5.dp)
            ) {
                content()
            }
        }

        // --- Overlays for resizing ---

        // Left Resize Handle
        if (resizeLeft != null) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(handleThickness)
                    .align(Alignment.CenterStart)
                    .pointerHoverIcon(PointerIcon(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.W_RESIZE_CURSOR)))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { activeDragCursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.W_RESIZE_CURSOR) },
                            onDragEnd = { activeDragCursor = null },
                            onDragCancel = { activeDragCursor = null }
                        ) { change, dragAmount ->
                            change.consume()
                            resizeLeft(dragAmount.x.toDp())
                        }
                    }
            )
        }

        // Right Resize Handle
        if (resizeRight != null) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(handleThickness)
                    .align(Alignment.CenterEnd)
                    .pointerHoverIcon(PointerIcon(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.E_RESIZE_CURSOR)))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { activeDragCursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.E_RESIZE_CURSOR) },
                            onDragEnd = { activeDragCursor = null },
                            onDragCancel = { activeDragCursor = null }
                        ) { change, dragAmount ->
                            change.consume()
                            resizeRight(dragAmount.x.toDp())
                        }
                    }
            )
        }

        // Top Resize Handle
        if (resizeTop != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(handleThickness)
                    .align(Alignment.TopCenter)
                    .pointerHoverIcon(PointerIcon(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.N_RESIZE_CURSOR)))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { activeDragCursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.N_RESIZE_CURSOR) },
                            onDragEnd = { activeDragCursor = null },
                            onDragCancel = { activeDragCursor = null }
                        ) { change, dragAmount ->
                            change.consume()
                            resizeTop(dragAmount.y.toDp())
                        }
                    }
            )
        }

        // Bottom Resize Handle
        if (resizeBottom != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(handleThickness)
                    .align(Alignment.BottomCenter)
                    .pointerHoverIcon(PointerIcon(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.S_RESIZE_CURSOR)))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { activeDragCursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.S_RESIZE_CURSOR) },
                            onDragEnd = { activeDragCursor = null },
                            onDragCancel = { activeDragCursor = null }
                        ) { change, dragAmount ->
                            change.consume()
                            resizeBottom(dragAmount.y.toDp())
                        }
                    }
            )
        }

        // Corner Resize Handles (2D resizing)
        // Bottom-Right Corner
        if (resizeRight != null && resizeBottom != null) {
            Box(
                modifier = Modifier
                    .size(handleThickness * 2)
                    .align(Alignment.BottomEnd)
                    .pointerHoverIcon(PointerIcon(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.SE_RESIZE_CURSOR)))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { activeDragCursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.SE_RESIZE_CURSOR) },
                            onDragEnd = { activeDragCursor = null },
                            onDragCancel = { activeDragCursor = null }
                        ) { change, dragAmount ->
                            change.consume()
                            resizeRight(dragAmount.x.toDp())
                            resizeBottom(dragAmount.y.toDp())
                        }
                    }
            )
        }

        // Bottom-Left Corner
        if (resizeLeft != null && resizeBottom != null) {
            Box(
                modifier = Modifier
                    .size(handleThickness * 2)
                    .align(Alignment.BottomStart)
                    .pointerHoverIcon(PointerIcon(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.SW_RESIZE_CURSOR)))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { activeDragCursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.SW_RESIZE_CURSOR) },
                            onDragEnd = { activeDragCursor = null },
                            onDragCancel = { activeDragCursor = null }
                        ) { change, dragAmount ->
                            change.consume()
                            resizeLeft(dragAmount.x.toDp())
                            resizeBottom(dragAmount.y.toDp())
                        }
                    }
            )
        }

        // Top-Right Corner
        if (resizeRight != null && resizeTop != null) {
            Box(
                modifier = Modifier
                    .size(handleThickness * 2)
                    .align(Alignment.TopEnd)
                    .pointerHoverIcon(PointerIcon(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.NE_RESIZE_CURSOR)))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { activeDragCursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.NE_RESIZE_CURSOR) },
                            onDragEnd = { activeDragCursor = null },
                            onDragCancel = { activeDragCursor = null }
                        ) { change, dragAmount ->
                            change.consume()
                            resizeRight(dragAmount.x.toDp())
                            resizeTop(dragAmount.y.toDp())
                        }
                    }
            )
        }

        // Top-Left Corner
        if (resizeLeft != null && resizeTop != null) {
            Box(
                modifier = Modifier
                    .size(handleThickness * 2)
                    .align(Alignment.TopStart)
                    .pointerHoverIcon(PointerIcon(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.NW_RESIZE_CURSOR)))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { activeDragCursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.NW_RESIZE_CURSOR) },
                            onDragEnd = { activeDragCursor = null },
                            onDragCancel = { activeDragCursor = null }
                        ) { change, dragAmount ->
                            change.consume()
                            resizeLeft(dragAmount.x.toDp())
                            resizeTop(dragAmount.y.toDp())
                        }
                    }
            )
        }

        // Active drag cursor lock overlay
        if (activeDragCursor != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerHoverIcon(PointerIcon(activeDragCursor!!))
            )
        }
    }
}
