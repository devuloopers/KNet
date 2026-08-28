package com.devuloopers.knet.ui.core.foundation.clipboard

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard

/** Writes plain text through the Android clipboard represented by this Compose [Clipboard]. */
actual suspend fun Clipboard.setPlainText(text: String) {
    setClipEntry(ClipEntry(ClipData.newPlainText("KNet", text)))
}

/** Reads the first plain-text item exposed by the Android clipboard. */
actual suspend fun Clipboard.readPlainText(): String? {
    val primaryClip = getClipEntry()?.clipData ?: return null
    if (primaryClip.itemCount == 0) return null
    return primaryClip.getItemAt(0).text?.toString()
}
