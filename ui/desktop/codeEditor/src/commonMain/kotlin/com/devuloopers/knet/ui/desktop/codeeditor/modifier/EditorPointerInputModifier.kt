package com.devuloopers.knet.ui.desktop.codeeditor.modifier

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.AutoScrollController
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.PointerHitTestEngine
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorDocumentSnapshot
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorPosition
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorSelection
import com.devuloopers.knet.ui.desktop.codeeditor.gesture.SelectionGestureHandler
import com.devuloopers.knet.ui.desktop.codeeditor.theme.CodeEditorTokens
import com.devuloopers.knet.ui.desktop.codeeditor.viewport.EditorVisualLineMap

/**
 * Custom pointer input modifier for [com.devuloopers.knet.ui.desktop.codeeditor.component.LazyCodeBody].
 *
 * Handles pointer drag selection, scrollbar drag ownership locking, cursor switching, and auto-scrolling.
 */
internal fun Modifier.editorPointerInput(
    snapshot: EditorDocumentSnapshot,
    visualLineMap: EditorVisualLineMap,
    containerHeightPx: Float,
    containerWidthPx: Float,
    gutterWidthPx: Float,
    lineHeightPx: Float,
    charWidthPx: Float,
    autoScrollThresholdPx: Float,
    lazyListState: LazyListState,
    lineTextLayoutMap: Map<Int, androidx.compose.ui.text.TextLayoutResult>,
    selectionGestureHandler: SelectionGestureHandler,
    autoScrollController: AutoScrollController,
    currentSelectionState: EditorSelection?,
    currentCaret: EditorPosition,
    updateCaret: (EditorPosition) -> Unit,
    updateSelection: (EditorSelection?) -> Unit
): Modifier = this.pointerInput(
    snapshot,
    visualLineMap,
    containerHeightPx,
    containerWidthPx,
    gutterWidthPx
) {
    val scrollbarWidthPx = CodeEditorTokens.ScrollbarHitZoneWidth.toPx()
    val dragOwnership = EditorPointerDragOwnership()

    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val isPressed = event.buttons.isPrimaryPressed
            val isShiftPressed = event.keyboardModifiers.isShiftPressed

            if (event.changes.isNotEmpty()) {
                val position = event.changes.first().position
                val isOverVerticalScrollbar = containerWidthPx > 0f &&
                    position.x >= (containerWidthPx - scrollbarWidthPx)
                val isOverHorizontalScrollbar = containerHeightPx > 0f &&
                    position.y >= (containerHeightPx - scrollbarWidthPx)
                val isOverScrollbarZone = isOverVerticalScrollbar || isOverHorizontalScrollbar
                val previousOwner = dragOwnership.owner
                val dragOwner = dragOwnership.update(isPressed, isOverScrollbarZone)
                val isInitialTextPress = dragOwner == EditorPointerDragOwner.Text &&
                    previousOwner == EditorPointerDragOwner.None

                // The initial press owns the complete gesture. Crossing the bottom scrollbar while
                // selecting must not cancel downward selection or its auto-scroll loop.
                if (dragOwner == EditorPointerDragOwner.Text) {
                    autoScrollController.handleDragPointerLazy(
                        mouseY = position.y,
                        containerHeightPx = containerHeightPx,
                        thresholdPx = autoScrollThresholdPx,
                        lazyListState = lazyListState
                    )

                    val hitResult = PointerHitTestEngine.calculatePointerHit(
                        pos = position,
                        lazyListState = lazyListState,
                        visualLineMap = visualLineMap,
                        snapshot = snapshot,
                        lineHeightPx = lineHeightPx,
                        charWidthPx = charWidthPx,
                        gutterWidthPx = gutterWidthPx,
                        containerWidthPx = containerWidthPx,
                        lineTextLayoutMap = lineTextLayoutMap
                    )

                    if (isInitialTextPress) {
                        updateCaret(EditorPosition(hitResult.documentLineIndex, hitResult.columnIndex))
                    }

                    selectionGestureHandler.processPointerEvent(
                        targetLineIndex = hitResult.documentLineIndex,
                        targetColIndex = hitResult.columnIndex,
                        lineText = hitResult.displayLineText,
                        isShiftPressed = isShiftPressed,
                        currentSelection = currentSelectionState,
                        caret = currentCaret,
                        onSelectionChange = updateSelection
                    )
                } else {
                    selectionGestureHandler.processPointerRelease(
                        isShiftPressed = isShiftPressed,
                        onSelectionChange = updateSelection
                    )
                    autoScrollController.stop()
                }
            }
        }
    }
}
