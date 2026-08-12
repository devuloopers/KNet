package com.devuloopers.knet.ui.desktop.codeeditor.gesture

import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.WordBoundaryEngine
import com.devuloopers.knet.ui.desktop.codeeditor.model.EditorSelection
import kotlin.math.abs

/**
 * Specialized gesture handler managing Double-Click word selection, Triple-Click line selection,
 * and multi-click word-by-word and line-by-line drag snapping calculations.
 *
 * Implements VS Code multi-click selection rules (`cursorWordOperations.ts`).
 */
class MultiClickGestureHandler {
    private var lastClickTimeMs: Long = 0L
    private var lastClickLineIndex: Int = -1
    private var lastClickColIndex: Int = -1

    /**
     * Current click sequence count (1 = single, 2 = double, 3 = triple).
     */
    var clickCount: Int = 0
        private set

    /**
     * Start column of double-clicked word anchor.
     */
    var wordAnchorStart: Int = 0
        private set

    /**
     * End column of double-clicked word anchor.
     */
    var wordAnchorEnd: Int = 0
        private set

    companion object {
        const val MULTI_CLICK_INTERVAL_MS = 300L
    }

    /**
     * Evaluates pointer press click count and returns calculated multi-click selection range, if applicable.
     *
     * @param targetLineIndex 0-indexed line index of pointer press.
     * @param targetColIndex 0-indexed column index of pointer press.
     * @param lineText String content of target line.
     * @param currentTimeMs Current timestamp in milliseconds.
     * @return [EditorSelection] if double/triple click occurred, or `null` for single click.
     */
    fun processClick(
        targetLineIndex: Int,
        targetColIndex: Int,
        lineText: String,
        currentTimeMs: Long = System.currentTimeMillis()
    ): EditorSelection? {
        val isSamePosition = (lastClickLineIndex == targetLineIndex && abs(lastClickColIndex - targetColIndex) <= 2)
        clickCount = if (currentTimeMs - lastClickTimeMs <= MULTI_CLICK_INTERVAL_MS && isSamePosition) {
            (clickCount % 3) + 1
        } else {
            1
        }
        lastClickTimeMs = currentTimeMs
        lastClickLineIndex = targetLineIndex
        lastClickColIndex = targetColIndex

        return when (clickCount) {
            2 -> {
                val (wordStart, wordEnd) = WordBoundaryEngine.findWordBounds(lineText, targetColIndex)
                wordAnchorStart = wordStart
                wordAnchorEnd = wordEnd
                EditorSelection(targetLineIndex, wordStart, targetLineIndex, wordEnd)
            }

            3 -> {
                EditorSelection(targetLineIndex, 0, targetLineIndex, lineText.length)
            }

            else -> null
        }
    }

    /**
     * Calculates word-by-word or line-by-line drag snapping range during active multi-click dragging.
     *
     * @param anchorLine 0-indexed line index of original drag anchor.
     * @param anchorCol 0-indexed column index of original drag anchor.
     * @param targetLineIndex 0-indexed current line index of pointer.
     * @param targetColIndex 0-indexed current column index of pointer.
     * @param lineText String content of target line.
     * @return [EditorSelection] snapped selection range, or `null` if not in multi-click mode.
     */
    fun processDrag(
        anchorLine: Int,
        anchorCol: Int,
        targetLineIndex: Int,
        targetColIndex: Int,
        lineText: String
    ): EditorSelection? {
        return when (clickCount) {
            2 -> {
                val (targetWordStart, targetWordEnd) = WordBoundaryEngine.findWordBounds(lineText, targetColIndex)
                val isForward =
                    (targetLineIndex > anchorLine || (targetLineIndex == anchorLine && targetColIndex >= anchorCol))
                val startCol = if (isForward) wordAnchorStart else wordAnchorEnd
                val endCol = if (isForward) targetWordEnd else targetWordStart
                EditorSelection(anchorLine, startCol, targetLineIndex, endCol)
            }

            3 -> {
                val isForward = targetLineIndex >= anchorLine
                val startCol = if (isForward) 0 else lineText.length
                val endCol = if (isForward) lineText.length else 0
                EditorSelection(anchorLine, startCol, targetLineIndex, endCol)
            }

            else -> null
        }
    }

    /**
     * Resets click sequence tracking state.
     */
    fun reset() {
        clickCount = 0
        lastClickTimeMs = 0L
        lastClickLineIndex = -1
        lastClickColIndex = -1
    }
}
