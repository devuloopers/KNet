package com.devuloopers.knet.companion.android.certificate

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore

/** Android MediaStore/Storage Access Framework implementation of public certificate storage. */
internal class MediaStoreAndroidCertificateStorage(
    private val contentResolver: ContentResolver,
) : AndroidCertificateStorage {
    override fun writeToDownloads(bytes: ByteArray): Boolean {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val existing = findExistingCertificate(collection)
        val destination = existing ?: contentResolver.insert(collection, pendingCertificateValues()) ?: return false
        val inserted = existing == null
        return try {
            if (!inserted) markPending(destination)
            require(write(destination, bytes))
            publish(destination)
            true
        } catch (_: Throwable) {
            if (inserted) runCatching { contentResolver.delete(destination, null, null) }
            if (!inserted) runCatching { publish(destination) }
            false
        }
    }

    override fun writeToDocument(bytes: ByteArray, destination: Uri): Boolean =
        runCatching { write(destination, bytes) }.getOrDefault(false)

    private fun write(destination: Uri, bytes: ByteArray): Boolean {
        val output = contentResolver.openOutputStream(destination, "rwt") ?: return false
        output.use {
            it.write(bytes)
            it.flush()
        }
        return true
    }

    private fun findExistingCertificate(collection: Uri): Uri? {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection =
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val arguments = arrayOf(
            AndroidCertificateExportPolicy.FILE_NAME,
            "${AndroidCertificateExportPolicy.downloadsDirectory}/",
        )
        return runCatching {
            contentResolver.query(collection, projection, selection, arguments, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                ContentUris.withAppendedId(collection, cursor.getLong(0))
            }
        }.getOrNull()
    }

    private fun pendingCertificateValues(): ContentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, AndroidCertificateExportPolicy.FILE_NAME)
        put(MediaStore.MediaColumns.MIME_TYPE, AndroidCertificateExportPolicy.MIME_TYPE)
        put(MediaStore.MediaColumns.RELATIVE_PATH, AndroidCertificateExportPolicy.downloadsDirectory)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }

    private fun markPending(destination: Uri) {
        contentResolver.update(
            destination,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 1) },
            null,
            null,
        )
    }

    private fun publish(destination: Uri) {
        contentResolver.update(
            destination,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null,
            null,
        )
    }
}
