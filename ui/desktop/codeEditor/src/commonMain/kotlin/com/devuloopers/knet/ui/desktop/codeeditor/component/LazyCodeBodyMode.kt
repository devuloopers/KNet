package com.devuloopers.knet.ui.desktop.codeeditor.component

/**
 * Sealed interface defining the rendering mode for [LazyCodeBody].
 *
 * Controls whether the virtualized viewport renders interactive [ReadOnly] text selection
 * or [Editable] single-line text field rows per document line.
 */
sealed interface LazyCodeBodyMode {

    /**
     * Renders each line as a non-editable [androidx.compose.material3.Text] composable
     * wrapped inside a [androidx.compose.foundation.text.selection.SelectionContainer].
     *
     * Supports mouse drag selection, right-click context menu copy, and Cmd+C / Ctrl+C
     * keyboard shortcuts. Suitable for HTTP response and request body inspection.
     */
    data object ReadOnly : LazyCodeBodyMode

    /**
     * Renders each line as an editable single-line
     * [androidx.compose.foundation.text.BasicTextField] composable.
     *
     * Supports real-time keyboard typing, caret navigation via arrow keys, Enter to split
     * lines, and Backspace at column 0 to merge lines. Suitable for API Studio request
     * body authoring and source code editing.
     */
    data object Editable : LazyCodeBodyMode
}
