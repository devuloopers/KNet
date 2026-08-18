package com.devuloopers.knet.ui.desktop.codeeditor.modifier

import java.awt.Cursor
import java.awt.KeyboardFocusManager

/** Desktop cursor bridge required by Compose's AWT window implementation. */
internal actual fun updateEditorPointerCursor(isScrollbarActive: Boolean) {
    runCatching {
        val activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
            ?: return@runCatching
        val targetCursorType = if (isScrollbarActive) Cursor.DEFAULT_CURSOR else Cursor.TEXT_CURSOR
        if (activeWindow.cursor.type != targetCursorType) {
            activeWindow.cursor = Cursor.getPredefinedCursor(targetCursorType)
        }
    }
}
