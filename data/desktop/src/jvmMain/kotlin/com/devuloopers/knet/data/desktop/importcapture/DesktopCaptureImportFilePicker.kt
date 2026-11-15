package com.devuloopers.knet.data.desktop.importcapture

import com.devuloopers.knet.application.contract.importcapture.*
import kotlinx.coroutines.suspendCancellableCoroutine
import java.awt.*
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Native open dialog on the AWT event thread; format validation remains with the parser. */
class DesktopCaptureImportFilePicker : CaptureImportFilePicker {
    override suspend fun choose(): String? = suspendCancellableCoroutine { continuation ->
        EventQueue.invokeLater {
            if (!continuation.isActive) return@invokeLater
            val picker = when (val window = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow) {
                is Dialog -> FileDialog(window, "Import capture", FileDialog.LOAD)
                is Frame -> FileDialog(window, "Import capture", FileDialog.LOAD)
                else -> FileDialog(null as Frame?, "Import capture", FileDialog.LOAD)
            }
            continuation.invokeOnCancellation { EventQueue.invokeLater { picker.dispose() } }
            try {
                picker.isVisible = true
                val selected = picker.file?.let { File(picker.directory.orEmpty(), it).absolutePath }
                if (continuation.isActive) continuation.resume(selected)
            } catch (failure: Exception) { if (continuation.isActive) continuation.resumeWithException(failure) }
            finally { picker.dispose() }
        }
    }
}
