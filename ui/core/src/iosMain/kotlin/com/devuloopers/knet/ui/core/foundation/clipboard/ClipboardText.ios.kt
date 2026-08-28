package com.devuloopers.knet.ui.core.foundation.clipboard

import androidx.compose.ui.platform.Clipboard
import platform.UIKit.UIPasteboard

/** Writes plain text through the iOS pasteboard represented by this Compose [Clipboard]. */
actual suspend fun Clipboard.setPlainText(text: String) {
    UIPasteboard.generalPasteboard.string = text
}

/** Reads plain text from the iOS pasteboard when one is available. */
actual suspend fun Clipboard.readPlainText(): String? = UIPasteboard.generalPasteboard.string
