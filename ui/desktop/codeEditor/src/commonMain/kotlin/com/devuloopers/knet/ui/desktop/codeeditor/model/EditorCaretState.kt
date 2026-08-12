package com.devuloopers.knet.ui.desktop.codeeditor.model

/**
 * State representing the active line and caret position within the code editor.
 *
 * @property lineIndex 0-indexed position of the line currently focused or containing the caret.
 * @property colIndex 0-indexed column character offset within the active line.
 */
data class EditorCaretState(
    val lineIndex: Int = 0,
    val colIndex: Int = 0
)