package com.devuloopers.knet.ui.desktop.codeeditor.api

import androidx.compose.ui.graphics.Color

/**
 * Operating mode for [com.devuloopers.knet.ui.desktop.codeeditor.api.KNetCodeEditor].
 */
sealed interface EditorMode {
    /**
     * Interactive full code editing mode with BasicTextField, undo/redo history,
     * auto-indentation, bracket matching, and placeholder support.
     */
    data class Editable(
        val onCodeChange: (String) -> Unit,
        val onPrettify: (() -> Unit)? = null,
        val placeholder: String = "",
        val textColor: Color = Color(0xFFA855F7)
    ) : EditorMode

    /**
     * Read-only inspection mode with LazyColumn virtualization, syntax highlighting,
     * selection/copy context menu, and search filter bar.
     */
    data object ReadOnly : EditorMode
}
