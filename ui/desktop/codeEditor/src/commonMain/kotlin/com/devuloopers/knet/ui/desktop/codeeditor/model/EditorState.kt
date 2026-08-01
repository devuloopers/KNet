package com.devuloopers.knet.ui.desktop.codeeditor.model

/**
 * State container for code editor document parameters.
 */
data class EditorState(
    val code: String = "",
    val activeLineIndex: Int = 0,
    val cursorPosition: CursorPosition = CursorPosition(),
    val selectionRange: SelectionRange = SelectionRange(),
    val isTruncated: Boolean = false
)
