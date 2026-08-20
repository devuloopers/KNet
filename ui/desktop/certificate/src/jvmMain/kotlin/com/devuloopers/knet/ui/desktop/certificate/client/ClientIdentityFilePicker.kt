package com.devuloopers.knet.ui.desktop.certificate.client

import java.awt.Dialog
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.io.File
import java.io.FilenameFilter
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/** File types accepted by the client-identity import flow. */
internal object ClientIdentityImportCapabilities {
    val extensions: Array<String> = arrayOf("p12", "pfx", "pem", "crt", "cer", "key", "jks", "keystore")
    val label: String = extensions.joinToString(" / ") { ".$it" }

    fun supports(fileName: String): Boolean = fileName
        .substringAfterLast('.', missingDelimiterValue = "")
        .lowercase() in extensions
}

/** Platform file-selection boundary kept outside the certificate composable. */
fun interface ClientIdentityFilePicker {
    fun chooseIdentity(onResult: (Result<String?>) -> Unit)
}

/**
 * Native system picker used by the desktop product on macOS, Windows, and Linux.
 *
 * The active Compose window is used as the modal owner when AWT can resolve it. Swing remains a
 * fallback for desktop environments where the native AWT peer cannot be created.
 */
object NativeClientIdentityFilePicker : ClientIdentityFilePicker {
    override fun chooseIdentity(onResult: (Result<String?>) -> Unit) {
        EventQueue.invokeLater {
            val selectedPath = runCatching(::openNativePicker)
                .recoverCatching { openSwingPicker() }
                .mapCatching(::validateSelection)
            onResult(selectedPath)
        }
    }

    private fun openNativePicker(): String? {
        val activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
        val picker = when (activeWindow) {
            is Frame -> FileDialog(activeWindow, DIALOG_TITLE, FileDialog.LOAD)
            is Dialog -> FileDialog(activeWindow, DIALOG_TITLE, FileDialog.LOAD)
            else -> FileDialog(null as Frame?, DIALOG_TITLE, FileDialog.LOAD)
        }
        return try {
            picker.isMultipleMode = false
            picker.filenameFilter = FilenameFilter { _, name ->
                ClientIdentityImportCapabilities.supports(name)
            }
            picker.isVisible = true
            picker.files.firstOrNull()?.absolutePath
                ?: picker.file?.let { name -> File(picker.directory.orEmpty(), name).absolutePath }
        } finally {
            picker.dispose()
        }
    }

    private fun validateSelection(path: String?): String? {
        if (path == null) return null
        require(ClientIdentityImportCapabilities.supports(File(path).name)) {
            "Unsupported client identity type. Select one of: ${ClientIdentityImportCapabilities.label}."
        }
        return path
    }
}

/** Cross-platform Swing fallback retained for environments without a native AWT file dialog. */
object SwingClientIdentityFilePicker : ClientIdentityFilePicker {
    override fun chooseIdentity(onResult: (Result<String?>) -> Unit) {
        EventQueue.invokeLater {
            onResult(
                runCatching(::openSwingPicker).mapCatching { selectedPath ->
                    selectedPath?.also { path ->
                        require(ClientIdentityImportCapabilities.supports(File(path).name)) {
                            "Unsupported client identity type. Select one of: ${ClientIdentityImportCapabilities.label}."
                        }
                    }
                },
            )
        }
    }
}

private fun openSwingPicker(): String? {
    val chooser = JFileChooser().apply {
        dialogTitle = DIALOG_TITLE
        fileFilter = FileNameExtensionFilter(
            "Client identity files (${ClientIdentityImportCapabilities.label})",
            *ClientIdentityImportCapabilities.extensions,
        )
        isAcceptAllFileFilterUsed = false
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile?.absolutePath
    } else {
        null
    }
}

private const val DIALOG_TITLE: String = "Select Client Identity"
