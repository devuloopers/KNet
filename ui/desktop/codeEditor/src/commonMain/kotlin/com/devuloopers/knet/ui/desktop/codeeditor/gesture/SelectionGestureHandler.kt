package com.devuloopers.knet.ui.desktop.codeeditor.gesture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.devuloopers.knet.ui.desktop.codeeditor.model.EditorCaretState
import com.devuloopers.knet.ui.desktop.codeeditor.model.EditorSelection

/**
 * Stateful viewport gesture controller managing pointer drag anchors, Shift + Click selection expansion,
 * single-click dismissal, and delegating multi-click gestures to [MultiClickGestureHandler].
 */
class SelectionGestureHandler(
    private val multiClickGestureHandler: MultiClickGestureHandler = MultiClickGestureHandler()
) {
    private var dragAnchor by mutableStateOf<Pair<Int, Int>?>(null)
    private var hasDragged by mutableStateOf(false)

    /**
     * Processes a pointer press or drag update event at target coordinate [targetLineIndex], [targetColIndex].
     *
     * @param targetLineIndex 0-indexed target line position.
     * @param targetColIndex 0-indexed target column position.
     * @param lineText Text string of the target line.
     * @param isShiftPressed True if Shift key is currently held down.
     * @param currentSelection Active viewport [EditorSelection], if any.
     * @param caretState Current caret position state [EditorCaretState], if any.
     * @param currentTimeMs Current timestamp in milliseconds.
     * @param onSelectionChange Callback fired with the calculated updated selection range.
     */
    fun processPointerEvent(
        targetLineIndex: Int,
        targetColIndex: Int,
        lineText: String = "",
        isShiftPressed: Boolean = false,
        currentSelection: EditorSelection? = null,
        caretState: EditorCaretState? = null,
        currentTimeMs: Long = System.currentTimeMillis(),
        onSelectionChange: (EditorSelection?) -> Unit
    ) {
        if (dragAnchor == null) {
            val multiClickSelection = multiClickGestureHandler.processClick(
                targetLineIndex = targetLineIndex,
                targetColIndex = targetColIndex,
                lineText = lineText,
                currentTimeMs = currentTimeMs
            )

            if (multiClickSelection != null) {
                val anchorCol = if (multiClickGestureHandler.clickCount == 2) {
                    multiClickGestureHandler.wordAnchorStart
                } else 0
                dragAnchor = targetLineIndex to anchorCol
                hasDragged = true
                onSelectionChange(multiClickSelection)
                return
            }

            dragAnchor = if (isShiftPressed && currentSelection != null && !currentSelection.isEmpty) {
                // Shift + Click: Preserve origin start anchor of existing selection
                currentSelection.startLine to currentSelection.startCol
            } else if (isShiftPressed && caretState != null) {
                // Shift + Click: Preserve current caret position as start anchor
                caretState.lineIndex to caretState.colIndex
            } else {
                // Standard press: Start new selection anchor at clicked coordinate
                targetLineIndex to targetColIndex
            }
        }

        val (anchorLine, anchorCol) = dragAnchor!!

        if (multiClickGestureHandler.clickCount >= 2) {
            val dragSelection = multiClickGestureHandler.processDrag(
                anchorLine = anchorLine,
                anchorCol = anchorCol,
                targetLineIndex = targetLineIndex,
                targetColIndex = targetColIndex,
                lineText = lineText
            )
            if (dragSelection != null) {
                hasDragged = true
                onSelectionChange(dragSelection)
                return
            }
        }

        if (isShiftPressed || anchorLine != targetLineIndex || anchorCol != targetColIndex) {
            hasDragged = true
            onSelectionChange(
                EditorSelection(
                    startLine = anchorLine,
                    startCol = anchorCol,
                    endLine = targetLineIndex,
                    endCol = targetColIndex
                )
            )
        }
    }

    /**
     * Handles pointer release when mouse button is unpressed.
     *
     * If mouse was clicked without dragging (`hasDragged == false`), [isShiftPressed] is false,
     * and [clickCount] < 2, invokes [onSelectionChange] with `null` to dismiss selection overlay.
     *
     * @param isShiftPressed True if Shift key is currently held down.
     * @param onSelectionChange Callback fired when selection state requires dismissal.
     */
    fun processPointerRelease(
        isShiftPressed: Boolean = false,
        onSelectionChange: (EditorSelection?) -> Unit
    ) {
        if (!hasDragged && dragAnchor != null && !isShiftPressed && multiClickGestureHandler.clickCount < 2) {
            onSelectionChange(null)
        }
        dragAnchor = null
        hasDragged = false
    }

    /**
     * Clears and resets all internal anchor states.
     */
    fun reset() {
        dragAnchor = null
        hasDragged = false
        multiClickGestureHandler.reset()
    }
}

/**
 * Creates and remembers a reusable [SelectionGestureHandler] instance across recompositions.
 */
@Composable
fun rememberSelectionGestureHandler(): SelectionGestureHandler {
    return remember { SelectionGestureHandler() }
}
