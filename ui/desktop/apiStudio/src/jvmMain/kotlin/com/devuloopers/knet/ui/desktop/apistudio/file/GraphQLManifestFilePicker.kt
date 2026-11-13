package com.devuloopers.knet.ui.desktop.apistudio.file

import java.awt.Dialog
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.io.File
import java.io.FilenameFilter

/** Native chooser returning only a path; bounded IO and validation belong to the import use case. */
fun interface GraphQLManifestFilePicker {
    /** Publishes a selected local path, null for cancellation, or a native picker failure. */
    fun choose(onResult: (Result<String?>) -> Unit)
}

/** Single JSON manifest selection on the AWT event thread. */
object NativeGraphQLManifestFilePicker : GraphQLManifestFilePicker {
    override fun choose(onResult: (Result<String?>) -> Unit) {
        EventQueue.invokeLater { onResult(runCatching(::open)) }
    }

    private fun open(): String? {
        val picker = when (val window = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow) {
            is Frame -> FileDialog(window, TITLE, FileDialog.LOAD)
            is Dialog -> FileDialog(window, TITLE, FileDialog.LOAD)
            else -> FileDialog(null as Frame?, TITLE, FileDialog.LOAD)
        }
        return try {
            picker.isMultipleMode = false
            picker.filenameFilter = FilenameFilter { _, name -> name.endsWith(".json", true) }
            picker.isVisible = true
            picker.files.firstOrNull()?.absolutePath
                ?: picker.file?.let { File(picker.directory.orEmpty(), it).absolutePath }
        } finally { picker.dispose() }
    }

    private const val TITLE = "Import GraphQL Persisted Queries"
}
