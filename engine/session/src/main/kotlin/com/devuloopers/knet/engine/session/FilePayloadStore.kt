package com.devuloopers.knet.engine.session

import com.devuloopers.knet.core.logger.KNetLogger
import java.io.File
import java.nio.file.Files

private const val TAG = "FilePayloadStore"

/**
 * Manages raw HTTP request/response payload file storage on disk using Java NIO APIs.
 *
 * @property baseDir Root storage folder for raw payload files.
 */
class FilePayloadStore(private val baseDir: File) {

    init {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
    }

    /**
     * Saves payload bytes to a file under the base directory.
     *
     * @param transactionId Unique transaction ID used as filename prefix.
     * @param suffix Indicator for request ("req") or response ("res").
     * @param bytes Raw payload bytes to write.
     * @return Absolute path of the saved file, or null if bytes were empty/null.
     */
    fun savePayload(transactionId: String, suffix: String, bytes: ByteArray?): String? {
        if (bytes == null || bytes.isEmpty()) return null
        return try {
            val file = File(baseDir, "${transactionId}_$suffix.bin")
            Files.write(file.toPath(), bytes)
            file.absolutePath
        } catch (e: Exception) {
            KNetLogger.error(TAG, e) { "Failed to save payload for $transactionId ($suffix)" }
            null
        }
    }

    /**
     * Reads raw payload bytes from a cached payload file.
     *
     * @param path Absolute file path of the cached payload.
     * @return Raw bytes, or null if invalid or missing.
     */
    fun loadPayload(path: String?): ByteArray? {
        if (path.isNullOrEmpty()) return null
        return try {
            val file = File(path)
            if (file.exists()) {
                Files.readAllBytes(file.toPath())
            } else {
                null
            }
        } catch (e: Exception) {
            KNetLogger.error(TAG, e) { "Failed to load payload from $path" }
            null
        }
    }

    /**
     * Deletes a cached payload file from disk.
     *
     * @param path Absolute file path of the payload to delete.
     * @return True if deletion succeeded.
     */
    fun deletePayload(path: String?): Boolean {
        if (path.isNullOrEmpty()) return false
        return try {
            val file = File(path)
            Files.deleteIfExists(file.toPath())
        } catch (e: Exception) {
            KNetLogger.error(TAG, e) { "Failed to delete payload file $path" }
            false
        }
    }

    /**
     * Clears all cached payload files in the root base directory.
     */
    fun clearStore() {
        try {
            baseDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    Files.deleteIfExists(file.toPath())
                }
            }
        } catch (e: Exception) {
            KNetLogger.error(TAG, e) { "Failed to clear payload store" }
        }
    }
}
