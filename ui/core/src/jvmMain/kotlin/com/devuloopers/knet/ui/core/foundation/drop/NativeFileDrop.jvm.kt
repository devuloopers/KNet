@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.devuloopers.knet.ui.core.foundation.drop

import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.awtTransferable
import java.awt.EventQueue
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import java.net.URI

/** Checks native file flavors without reading file contents or consuming the transfer. */
internal fun acceptsLocalFileDrop(event: DragAndDropEvent): Boolean = runCatching {
    event.awtTransferable.let { data ->
        data.isDataFlavorSupported(DataFlavor.javaFileListFlavor) ||
            data.transferDataFlavors.any { it.isMimeTypeEqual("text/uri-list") }
    }
}.getOrDefault(false)

/** Negotiates a non-destructive copy and publishes decoded paths after native drop completion. */
internal fun receiveLocalFileDrop(
    event: DragAndDropEvent,
    onDropped: (List<String>) -> Unit,
    onRejected: (String) -> Unit,
): Boolean {
    return try {
        val native = requireNotNull(event.nativeEvent as? DropTargetDropEvent) { "The native file transfer is unavailable." }
        require(native.sourceActions and DnDConstants.ACTION_COPY != 0) { "The source must allow copying the capture file." }
        native.acceptDrop(DnDConstants.ACTION_COPY)
        queueLocalFileDrop(event.awtTransferable, { work -> EventQueue.invokeLater(work) }, onDropped, onRejected)
    } catch (failure: Exception) {
        val message = failure.message ?: "Unable to accept the dropped files."
        EventQueue.invokeLater { onRejected(message) }
        false
    }
}

/**
 * Snapshots paths while the native transfer is valid, then queues presentation until the drop returns.
 * The callbacks are captured at release so a later workspace change cannot retarget the import.
 */
internal fun queueLocalFileDrop(
    transferable: Transferable,
    dispatch: (() -> Unit) -> Unit,
    onDropped: (List<String>) -> Unit,
    onRejected: (String) -> Unit,
): Boolean {
    val paths = try { localDroppedFiles(transferable) }
    catch (failure: Exception) {
        val message = failure.message ?: "Unable to read the dropped files."
        dispatch { onRejected(message) }
        return false
    }
    // Keep parsing outside AWT's dropComplete/onEnded sequence; presentation was already reserved.
    dispatch { onDropped(paths) }
    return true
}

/** Bounded local-file decoding shared by native drop targets; remote resources are never fetched. */
internal fun localDroppedFiles(transferable: Transferable): List<String> {
    val paths = if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
        val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>
            ?: error("The drop does not contain local files.")
        require(files.size in 1..MAXIMUM_DROP_FILES) { "Drop one capture file at a time." }
        files.map { (it as? File)?.absolutePath ?: error("The drop contains an unsupported item.") }
    } else {
        val flavor = transferable.transferDataFlavors.firstOrNull { it.isMimeTypeEqual("text/uri-list") && it.representationClass == String::class.java }
            ?: error("Drop a local capture file.")
        val value = transferable.getTransferData(flavor) as? String ?: error("The file list is unavailable.")
        require(value.length <= MAXIMUM_URI_LIST_LENGTH) { "The dropped file list is too large." }
        value.lineSequence().map(String::trim).filter { it.isNotEmpty() && !it.startsWith('#') }.take(MAXIMUM_DROP_FILES + 1).map { line ->
            val uri = URI(line)
            require(uri.scheme.equals("file", true) && uri.rawQuery == null && uri.rawFragment == null && uri.rawAuthority.isNullOrEmpty()) { "Only local files can be imported." }
            File(uri).absolutePath
        }.toList()
    }
    require(paths.size in 1..MAXIMUM_DROP_FILES) { "Drop one capture file at a time." }
    return paths
}

private const val MAXIMUM_DROP_FILES = 16
private const val MAXIMUM_URI_LIST_LENGTH = 65_536
