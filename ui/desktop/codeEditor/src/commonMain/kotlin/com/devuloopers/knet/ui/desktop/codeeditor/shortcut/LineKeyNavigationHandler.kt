package com.devuloopers.knet.ui.desktop.codeeditor.shortcut

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * Single-responsibility handler for evaluating single-line key events in [com.devuloopers.knet.ui.desktop.codeeditor.component.viewport.EditableLineContent].
 *
 * Handles arrow key navigation, line splitting, line merging, and Undo/Redo key events.
 */
internal object LineKeyNavigationHandler {

    /**
     * Evaluates a Compose key event for line-level navigation and actions.
     *
     * @param keyEvent Keyboard event to evaluate.
     * @param caretCol Current logical-line caret offset.
     * @param isCollapsed Whether the native text selection is collapsed.
     * @param textLength Current logical-line text length.
     * @param isWordWrapEnabled Whether the logical line may contain multiple visual rows.
     * @param currentVisualLineIndex Current visual row reported by text layout, or `null` before measurement.
     * @param visualLineCount Number of visual rows reported by text layout.
     * @param onNavigateUp Moves to the preceding logical line.
     * @param onNavigateDown Moves to the following logical line.
     * @param onNavigateLeftAtStart Moves to the previous logical-line end.
     * @param onNavigateRightAtEnd Moves to the following logical-line start.
     * @param onLineMerge Removes the preceding logical newline.
     * @param onLineSplit Inserts a logical newline at the supplied column.
     * @param onUndo Optional undo action.
     * @param onRedo Optional redo action.
     * @return `true` if event was consumed, `false` to propagate.
     */
    fun handleLineKeyEvent(
        keyEvent: KeyEvent,
        caretCol: Int,
        isCollapsed: Boolean,
        textLength: Int,
        isWordWrapEnabled: Boolean,
        currentVisualLineIndex: Int?,
        visualLineCount: Int,
        onNavigateUp: (targetCol: Int) -> Unit,
        onNavigateDown: (targetCol: Int) -> Unit,
        onNavigateLeftAtStart: () -> Unit,
        onNavigateRightAtEnd: () -> Unit,
        onLineMerge: () -> Unit,
        onLineSplit: (colIndex: Int) -> Unit,
        onUndo: (() -> Unit)?,
        onRedo: (() -> Unit)?
    ): Boolean {
        if (keyEvent.type != KeyEventType.KeyDown) return false
        val isCmdOrCtrl = keyEvent.isMetaPressed || keyEvent.isCtrlPressed

        if (isCmdOrCtrl && keyEvent.key == Key.Z) {
            if (keyEvent.isShiftPressed) {
                onRedo?.invoke()
            } else {
                onUndo?.invoke()
            }
            return true
        } else if (isCmdOrCtrl && keyEvent.key == Key.Y) {
            onRedo?.invoke()
            return true
        }

        return when (keyEvent.key) {
            Key.DirectionUp -> {
                if (
                    VerticalLineNavigationPolicy.shouldMoveToAdjacentLogicalLine(
                        direction = VerticalNavigationDirection.Up,
                        isWordWrapEnabled = isWordWrapEnabled,
                        currentVisualLineIndex = currentVisualLineIndex,
                        visualLineCount = visualLineCount
                    )
                ) {
                    onNavigateUp(caretCol)
                    true
                } else {
                    false
                }
            }
            Key.DirectionDown -> {
                if (
                    VerticalLineNavigationPolicy.shouldMoveToAdjacentLogicalLine(
                        direction = VerticalNavigationDirection.Down,
                        isWordWrapEnabled = isWordWrapEnabled,
                        currentVisualLineIndex = currentVisualLineIndex,
                        visualLineCount = visualLineCount
                    )
                ) {
                    onNavigateDown(caretCol)
                    true
                } else {
                    false
                }
            }
            Key.DirectionLeft -> {
                if (caretCol == 0 && isCollapsed) {
                    onNavigateLeftAtStart()
                    true
                } else false
            }
            Key.DirectionRight -> {
                if (caretCol == textLength && isCollapsed) {
                    onNavigateRightAtEnd()
                    true
                } else false
            }
            Key.Backspace -> {
                if (caretCol == 0 && isCollapsed) {
                    onLineMerge()
                    true
                } else false
            }
            Key.Enter -> {
                onLineSplit(caretCol)
                true
            }
            else -> false
        }
    }
}

/** Direction requested by a vertical caret-navigation key. */
internal enum class VerticalNavigationDirection {
    /** Move toward the preceding visual row. */
    Up,

    /** Move toward the following visual row. */
    Down
}

/** Decides whether vertical navigation leaves the active logical-line text field. */
internal object VerticalLineNavigationPolicy {
    /**
     * Returns whether the editor should move into an adjacent logical line.
     *
     * A wrapped text field retains Up/Down while another visual row exists inside that field.
     * The editor takes ownership only at its first or final visual row. Non-wrapped fields always
     * delegate vertical navigation to the surrounding logical-line editor.
     *
     * @param direction Requested vertical direction.
     * @param isWordWrapEnabled Whether the active logical line may contain multiple visual rows.
     * @param currentVisualLineIndex Current zero-based visual row, or `null` before layout.
     * @param visualLineCount Number of visual rows reported by text layout.
     * @return `true` when navigation should cross a logical-line boundary.
     */
    fun shouldMoveToAdjacentLogicalLine(
        direction: VerticalNavigationDirection,
        isWordWrapEnabled: Boolean,
        currentVisualLineIndex: Int?,
        visualLineCount: Int
    ): Boolean {
        if (!isWordWrapEnabled || currentVisualLineIndex == null || visualLineCount <= 1) return true
        val safeVisualLine = currentVisualLineIndex.coerceIn(0, visualLineCount - 1)
        return when (direction) {
            VerticalNavigationDirection.Up -> safeVisualLine == 0
            VerticalNavigationDirection.Down -> safeVisualLine == visualLineCount - 1
        }
    }
}
