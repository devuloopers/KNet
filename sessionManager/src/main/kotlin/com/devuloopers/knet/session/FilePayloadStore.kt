package com.devuloopers.knet.session

import com.devuloopers.knet.logger.KNetLogger
import java.io.File

private const val TAG = "FilePayloadStore"

/**
 * Manages caching raw HTTP request and response payload bytes to files on disk.
 *
 * @property baseDir The root folder where payload files are stored.
 */
class FilePayloadStore(private val baseDir: File) {

    init {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
    }

    /**
     * Saves raw payload bytes to a file under the base directory.
     *
     * @param transactionId Unique transaction ID used as filename prefix.
     * @param suffix Indicator for request ("req") or response ("res").
     * @param bytes The raw payload payload bytes.
     * @return The absolute path of the saved file, or null if bytes were empty or null.
     */
    fun savePayload(transactionId: String, suffix: String, bytes: ByteArray?): String? {
        if (bytes == null || bytes.isEmpty()) return null
        return try {
            val file = File(baseDir, "${transactionId}_$suffix.bin")
            file.writeBytes(bytes)
            file.absolutePath
        } catch (e: Exception) {
            KNetLogger.error(TAG, e) { "Failed to save payload for $transactionId ($suffix)" }
            null
        }
    }

    /**
     * Reads raw payload bytes from a cached file.
     *
     * @param path The absolute path to the cached payload file.
     * @return The read bytes, or null if path is invalid or empty.
     */
    fun loadPayload(path: String?): ByteArray? {
        if (path.isNullOrEmpty()) return null
        return try {
            val file = File(path)
            if (file.exists()) {
                file.readBytes()
            } else {
                null
            }
        } catch (e: Exception) {
            KNetLogger.error(TAG, e) { "Failed to load payload from $path" }
            null
        }
    }

    /**
     * Deletes a single cached payload file.
     *
     * @param path The absolute path to the cached payload file.
     * @return True if deletion completed successfully.
     */
    fun deletePayload(path: String?): Boolean {
        if (path.isNullOrEmpty()) return false
        return try {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            KNetLogger.error(TAG, e) { "Failed to delete payload file $path" }
            false
        }
    }

    /**
     * Clears all cached payload files inside the base directory.
     */
    fun clearStore() {
        try {
            baseDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            KNetLogger.error(TAG, e) { "Failed to clear payload store" }
        }
    }
}
