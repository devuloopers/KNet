package com.devuloopers.knet.ui.core.foundation.clipboard

import androidx.compose.ui.platform.Clipboard

/**
 * Writes [text] to this platform clipboard as plain text.
 *
 * Compose Desktop currently requires a JVM transfer object to construct its clip entry. That
 * platform detail is isolated in the `jvmMain` implementation instead of leaking into reusable UI.
 *
 * @param text Plain text to place on the clipboard.
 */
expect suspend fun Clipboard.setPlainText(text: String)

/**
 * Reads plain text from this platform clipboard when the current entry supports it.
 *
 * @return Clipboard text, or `null` when no plain-text representation is available.
 */
expect suspend fun Clipboard.readPlainText(): String?
