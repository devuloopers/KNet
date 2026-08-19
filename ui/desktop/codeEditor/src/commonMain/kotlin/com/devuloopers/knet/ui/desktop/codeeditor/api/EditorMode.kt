package com.devuloopers.knet.ui.desktop.codeeditor.api

/** Operating mode for the reusable code editor surface. */
enum class EditorMode {
    /** Virtualized inspection mode without document mutation commands. */
    ReadOnly,

    /** Interactive editing mode with caret, selection, commands, and delta undo/redo. */
    Editable
}
