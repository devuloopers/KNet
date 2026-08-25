package com.devuloopers.knet.ui.desktop.apistudio.grpc.file

import java.awt.*
import java.io.File
import java.io.FilenameFilter

fun interface GrpcDescriptorFilePicker {
    fun choose(onResult: (Result<String?>) -> Unit)
}

/** Native desktop picker for binary protobuf descriptor sets. */
object NativeGrpcDescriptorFilePicker : GrpcDescriptorFilePicker {
    override fun choose(onResult: (Result<String?>) -> Unit) {
        EventQueue.invokeLater { onResult(runCatching(::open)) }
    }

    private fun open(): String? {
        val picker = when (val activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow) {
            is Frame -> FileDialog(activeWindow, TITLE, FileDialog.LOAD)
            is Dialog -> FileDialog(activeWindow, TITLE, FileDialog.LOAD)
            else -> FileDialog(null as Frame?, TITLE, FileDialog.LOAD)
        }
        return try {
            picker.isMultipleMode = false
            picker.filenameFilter = FilenameFilter { _, name ->
                name.endsWith(".desc", true) || name.endsWith(".protoset", true) || name.endsWith(".pb", true)
            }
            picker.isVisible = true
            picker.files.firstOrNull()?.absolutePath
                ?: picker.file?.let { name -> File(picker.directory.orEmpty(), name).absolutePath }
        } finally {
            picker.dispose()
        }
    }

    private const val TITLE = "Import Protobuf Descriptor Set"
}
