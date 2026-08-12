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
object LineKeyNavigationHandler {

    /**
     * Evaluates a Compose key event for line-level navigation and actions.
     *
     * @return `true` if event was consumed, `false` to propagate.
     */
    fun handleLineKeyEvent(
        keyEvent: KeyEvent,
        caretCol: Int,
        isCollapsed: Boolean,
        textLength: Int,
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
                onNavigateUp(caretCol)
                true
            }
            Key.DirectionDown -> {
                onNavigateDown(caretCol)
                true
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
