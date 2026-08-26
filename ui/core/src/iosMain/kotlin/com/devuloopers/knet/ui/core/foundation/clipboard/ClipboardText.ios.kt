package com.devuloopers.knet.ui.core.foundation.clipboard

import androidx.compose.ui.platform.Clipboard

/** Writes plain text through the iOS pasteboard represented by this Compose [Clipboard]. */
actual suspend fun Clipboard.setPlainText(text: String) {
    nativeClipboard.string = text
}

/** Reads plain text from the iOS pasteboard when one is available. */
actual suspend fun Clipboard.readPlainText(): String? = nativeClipboard.string
