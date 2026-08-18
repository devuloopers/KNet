@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.devuloopers.knet.ui.core.foundation.clipboard

import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.asAwtTransferable
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

/** Desktop implementation backed by Compose's AWT-compatible clip entry. */
actual suspend fun Clipboard.setPlainText(text: String) {
    setClipEntry(ClipEntry(StringSelection(text)))
}

/** Desktop implementation reading the plain-text flavor from Compose's clip entry. */
actual suspend fun Clipboard.readPlainText(): String? {
    val transferable = getClipEntry()?.asAwtTransferable ?: return null
    if (!transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) return null
    return runCatching { transferable.getTransferData(DataFlavor.stringFlavor) as? String }.getOrNull()
}
