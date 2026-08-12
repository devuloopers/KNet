package com.devuloopers.knet.ui.desktop.codeeditor.modifier

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.AutoScrollController
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.FoldRegion
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.LazyLine
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.PointerHitTestEngine
import com.devuloopers.knet.ui.desktop.codeeditor.model.EditorCaretState
import com.devuloopers.knet.ui.desktop.codeeditor.component.LazyCodeBodyMode
import com.devuloopers.knet.ui.desktop.codeeditor.gesture.SelectionGestureHandler
import com.devuloopers.knet.ui.desktop.codeeditor.model.EditorSelection

/**
 * Custom pointer input modifier for [com.devuloopers.knet.ui.desktop.codeeditor.component.LazyCodeBody].
 *
 * Handles pointer drag selection, scrollbar drag ownership locking, cursor switching, and auto-scrolling.
 */
fun Modifier.editorPointerInput(
    rawLines: List<String>,
    visibleLines: List<LazyLine>,
    foldRegions: List<FoldRegion>,
    collapsedFoldStartLines: Set<Int>,
    mode: LazyCodeBodyMode,
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
    currentCaretState: EditorCaretState?,
    updateSelection: (EditorSelection?) -> Unit
): Modifier = this.pointerInput(
    rawLines,
    visibleLines,
    foldRegions,
    collapsedFoldStartLines,
    mode,
    containerHeightPx,
    containerWidthPx,
    gutterWidthPx
) {
    val scrollbarWidthPx = 16.dp.toPx()
    var isScrollbarDragging = false
    var wasPressed = false

    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val isPressed = event.buttons.isPrimaryPressed
            val isShiftPressed = event.keyboardModifiers.isShiftPressed

            if (event.changes.isNotEmpty()) {
                val position = event.changes.first().position
                val isOverScrollbarZone = containerWidthPx > 0f && position.x >= (containerWidthPx - scrollbarWidthPx)

                if (isPressed) {
                    if (!wasPressed) {
                        // Lock scrollbar drag ownership if initial click started inside scrollbar zone
                        isScrollbarDragging = isOverScrollbarZone
                    }
                } else {
                    isScrollbarDragging = false
                }
                wasPressed = isPressed

                val isScrollbarActive = isScrollbarDragging || isOverScrollbarZone

                try {
                    val activeWindow = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
                    if (activeWindow != null) {
                        val targetCursorType =
                            if (isScrollbarActive) java.awt.Cursor.DEFAULT_CURSOR else java.awt.Cursor.TEXT_CURSOR
                        if (activeWindow.cursor.type != targetCursorType) {
                            activeWindow.cursor = java.awt.Cursor.getPredefinedCursor(targetCursorType)
                        }
                    }
                } catch (_: Throwable) { }

                // Skip text selection processing & auto-scroll when scrollbar drag is active
                if (!isScrollbarActive && isPressed) {
                    autoScrollController.handleDragPointerLazy(
                        mouseY = position.y,
                        containerHeightPx = containerHeightPx,
                        thresholdPx = autoScrollThresholdPx,
                        lazyListState = lazyListState
                    )

                    val hitResult = PointerHitTestEngine.calculatePointerHit(
                        pos = position,
                        lazyListState = lazyListState,
                        visibleLines = visibleLines,
                        rawLines = rawLines,
                        lineHeightPx = lineHeightPx,
                        charWidthPx = charWidthPx,
                        gutterWidthPx = gutterWidthPx,
                        containerWidthPx = containerWidthPx,
                        lineTextLayoutMap = lineTextLayoutMap
                    )

                    selectionGestureHandler.processPointerEvent(
                        targetLineIndex = hitResult.rawLineIndex,
                        targetColIndex = hitResult.colIndex,
                        lineText = hitResult.displayLineText,
                        isShiftPressed = isShiftPressed,
                        currentSelection = currentSelectionState,
                        caretState = currentCaretState,
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
