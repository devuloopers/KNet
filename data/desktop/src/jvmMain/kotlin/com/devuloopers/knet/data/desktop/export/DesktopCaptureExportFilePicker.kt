package com.devuloopers.knet.data.desktop.export

import com.devuloopers.knet.application.contract.export.*
import kotlinx.coroutines.suspendCancellableCoroutine
import java.awt.*
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Native save dialog on the AWT event thread; overwrite decisions remain with the platform. */
class DesktopCaptureExportFilePicker : CaptureExportFilePicker {
    override suspend fun choose(format: CaptureExportFormat): String? = suspendCancellableCoroutine { continuation ->
        EventQueue.invokeLater {
            if (!continuation.isActive) return@invokeLater
            val picker = when (val window = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow) {
                is Dialog -> FileDialog(window, "Export traffic", FileDialog.SAVE)
                is Frame -> FileDialog(window, "Export traffic", FileDialog.SAVE)
                else -> FileDialog(null as Frame?, "Export traffic", FileDialog.SAVE)
            }
            continuation.invokeOnCancellation { EventQueue.invokeLater { picker.dispose() } }
            try {
                picker.file = "KNet-capture.${format.extension}"
                picker.isVisible = true
                val selected = picker.file?.let { File(picker.directory.orEmpty(), it).absolutePath }
                if (selected != null) require(selected.endsWith(".${format.extension}", true)) { "Choose a .${format.extension} filename." }
                if (continuation.isActive) continuation.resume(selected)
            } catch (failure: Exception) { if (continuation.isActive) continuation.resumeWithException(failure) }
            finally { picker.dispose() }
        }
    }
}
