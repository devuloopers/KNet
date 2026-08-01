package com.devuloopers.knet.ui.core.util

import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString

/**
 * 100% KMP helper for system clipboard operations.
 *
 * Decoupled from platform-specific APIs.
 */
public object KNetClipboard {
    /**
     * Sets [text] to the system clipboard via [ClipboardManager].
     *
     * @param clipboardManager The [ClipboardManager] instance obtained via `LocalClipboardManager.current`.
     * @param text The text to copy.
     */
    public fun copyToClipboard(clipboardManager: ClipboardManager, text: String) {
        if (text.isNotEmpty()) {
            clipboardManager.setText(AnnotatedString(text))
        }
    }
}
