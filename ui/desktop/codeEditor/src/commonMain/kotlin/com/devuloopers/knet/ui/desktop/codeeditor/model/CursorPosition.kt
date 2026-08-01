package com.devuloopers.knet.ui.desktop.codeeditor.model

/**
 * Encapsulates line and column cursor location inside the editor.
 */
data class CursorPosition(
    val line: Int = 0,
    val column: Int = 0
)
