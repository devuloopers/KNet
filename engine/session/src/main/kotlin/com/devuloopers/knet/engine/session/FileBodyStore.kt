package com.devuloopers.knet.engine.session

import com.devuloopers.knet.application.port.traffic.BodyAppendResult
import com.devuloopers.knet.application.port.traffic.BodyChunk
import com.devuloopers.knet.application.port.traffic.BodyDeleteResult
import com.devuloopers.knet.application.port.traffic.BodyFinalizeResult
import com.devuloopers.knet.application.port.traffic.BodyIntegrityExpectation
import com.devuloopers.knet.application.port.traffic.BodyIntegrityResult
import com.devuloopers.knet.application.port.traffic.BodyObjectInventoryPage
import com.devuloopers.knet.application.port.traffic.BodyRange
import com.devuloopers.knet.application.port.traffic.BodyStorageKey
import com.devuloopers.knet.application.port.traffic.BodyStorePort
import com.devuloopers.knet.application.port.traffic.BodyStoreMaintenancePort
import com.devuloopers.knet.application.port.traffic.BodyWritePolicy
import com.devuloopers.knet.application.port.traffic.BodyWriteSession
import com.devuloopers.knet.traffic.id.BodyId
import com.devuloopers.knet.traffic.model.body.BodyCaptureOutcome
import com.devuloopers.knet.traffic.model.body.BodyDigest
import com.devuloopers.knet.traffic.model.body.BodyDigestAlgorithm
import com.devuloopers.knet.traffic.model.body.BodyRef
import com.devuloopers.knet.traffic.model.body.ContentEncoding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Atomic owner-only file implementation of [BodyStorePort].
 *
 * Final paths are derived from a SHA-256 digest of opaque [BodyId] values. Writes remain in a
 * private temporary directory until fsync and atomic rename complete.
 *
 * @property root Canonical root that owns temporary and finalized body objects.
 */
class FileBodyStore(
    root: File,
) : BodyStorePort, BodyStoreMaintenancePort {
    private val rootDirectory = root.canonicalFile
    private val objectDirectory = rootDirectory.resolve("objects")
    private val temporaryDirectory = rootDirectory.resolve("tmp")
    private val activeBodyIds = ConcurrentHashMap<String, Unit>()

    init {
        require(SessionFileSecurity.secureDirectory(rootDirectory)) { "Body-store root could not be created." }
        require(SessionFileSecurity.secureDirectory(objectDirectory)) { "Body object directory could not be created." }
        require(SessionFileSecurity.secureDirectory(temporaryDirectory)) { "Body temporary directory could not be created." }
    }

    override suspend fun openWrite(
        bodyId: BodyId,
        policy: BodyWritePolicy,
        contentEncoding: ContentEncoding?,
    ): BodyWriteSession = withContext(Dispatchers.IO) {
        check(activeBodyIds.putIfAbsent(bodyId.value, Unit) == null) {
            "Body ${bodyId.value} already has an active writer."
        }
        val destination = objectFile(bodyId)
        try {
            check(!destination.exists()) { "Body ${bodyId.value} is already finalized." }
            SessionFileSecurity.secureDirectory(destination.parentFile)
            val temporary = Files.createTempFile(temporaryDirectory.toPath(), "body-", ".tmp").toFile()
            SessionFileSecurity.secureFile(temporary)
            FileBodyWriteSession(
                bodyId = bodyId,
                policy = policy,
                contentEncoding = contentEncoding,
                temporary = temporary,
                destination = destination,
                onTerminal = { activeBodyIds.remove(bodyId.value) },
            )
        } catch (failure: Throwable) {
            activeBodyIds.remove(bodyId.value)
            throw failure
        }
    }

    override suspend fun readBody(bodyId: BodyId, range: BodyRange): BodyChunk = withContext(Dispatchers.IO) {
        val file = objectFile(bodyId)
        check(file.isFile) { "Body ${bodyId.value} is unavailable." }
        RandomAccessFile(file, "r").use { input ->
            val fileLength = input.length()
            if (range.offset >= fileLength) {
                return@use BodyChunk(ByteArray(0), range.offset, endOfBody = true)
            }
            input.seek(range.offset)
            val length = minOf(range.length.toLong(), fileLength - range.offset).toInt()
            val bytes = ByteArray(length)
            input.readFully(bytes)
            BodyChunk(
                bytes = bytes,
                offset = range.offset,
                endOfBody = range.offset + length >= fileLength,
            )
        }
    }

    override suspend fun delete(bodyId: BodyId): BodyDeleteResult = withContext(Dispatchers.IO) {
        check(!activeBodyIds.containsKey(bodyId.value)) { "Cannot delete a body with an active writer." }
        if (Files.deleteIfExists(objectFile(bodyId).toPath())) {
            BodyDeleteResult.DELETED
        } else {
            BodyDeleteResult.NOT_FOUND
        }
    }

    override suspend fun reconcileTemporaryObjects(): Int = withContext(Dispatchers.IO) {
        var deleted = 0
        temporaryDirectory.listFiles()?.forEach { candidate ->
            if (candidate.isFile && candidate.extension == "tmp" && candidate.canonicalFile.parentFile == temporaryDirectory) {
                if (Files.deleteIfExists(candidate.toPath())) deleted += 1
            }
        }
        deleted
    }

    override fun storageKey(bodyId: BodyId): BodyStorageKey =
        BodyStorageKey(sha256Hex(bodyId.value.toByteArray(Charsets.UTF_8)))

    override suspend fun inventoryFinalizedObjects(
        after: BodyStorageKey?,
        limit: Int,
    ): BodyObjectInventoryPage = withContext(Dispatchers.IO) {
        require(limit in 1..1_000) { "Body object inventory limit must be between 1 and 1000." }
        after?.let(::requireValidStorageKey)
        val collected = ArrayList<BodyStorageKey>(limit + 1)
        val directories = objectDirectory.listFiles()
            .orEmpty()
            .filter { candidate -> candidate.isDirectory && SHARD_PATTERN.matches(candidate.name) }
            .sortedBy(File::getName)
        for (directory in directories) {
            val files = directory.listFiles()
                .orEmpty()
                .filter { candidate -> candidate.isFile && OBJECT_FILE_PATTERN.matches(candidate.name) }
                .sortedBy(File::getName)
            for (file in files) {
                val value = file.name.removeSuffix(OBJECT_SUFFIX)
                if (after != null && value <= after.value) continue
                collected += BodyStorageKey(value)
                if (collected.size > limit) break
            }
            if (collected.size > limit) break
        }
        val page = collected.take(limit)
        BodyObjectInventoryPage(
            keys = page,
            nextCursor = if (collected.size > limit) page.last() else null,
        )
    }

    override suspend fun deleteByStorageKey(key: BodyStorageKey): BodyDeleteResult = withContext(Dispatchers.IO) {
        requireValidStorageKey(key)
        if (Files.deleteIfExists(objectFile(key).toPath())) {
            BodyDeleteResult.DELETED
        } else {
            BodyDeleteResult.NOT_FOUND
        }
    }

    override suspend fun verifyByStorageKey(
        key: BodyStorageKey,
        expectation: BodyIntegrityExpectation,
        maximumDigestBytes: Long,
    ): BodyIntegrityResult = withContext(Dispatchers.IO) {
        require(maximumDigestBytes >= 0L) { "Integrity digest byte limit must not be negative." }
        requireValidStorageKey(key)
        val file = objectFile(key)
        if (!file.isFile) return@withContext BodyIntegrityResult.MISSING
        if (file.length() != expectation.storedBytes) return@withContext BodyIntegrityResult.SIZE_MISMATCH
        val expectedDigest = expectation.sha256 ?: return@withContext BodyIntegrityResult.VALID
        if (file.length() > maximumDigestBytes) {
            return@withContext BodyIntegrityResult.DIGEST_DEFERRED_BY_BYTE_LIMIT
        }
        val actualDigest = file.inputStream().buffered().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(INTEGRITY_BUFFER_BYTES)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            digest.digest().toHex()
        }
        if (actualDigest.equals(expectedDigest, ignoreCase = true)) {
            BodyIntegrityResult.VALID
        } else {
            BodyIntegrityResult.DIGEST_MISMATCH
        }
    }

    /** Resolves a contained two-level final path without embedding the raw body identifier. */
    private fun objectFile(bodyId: BodyId): File {
        return objectFile(storageKey(bodyId))
    }

    /** Resolves a contained final path for a validated opaque storage key. */
    private fun objectFile(key: BodyStorageKey): File {
        requireValidStorageKey(key)
        val directory = objectDirectory.resolve(key.value.take(2)).canonicalFile
        check(directory.toPath().startsWith(objectDirectory.toPath())) { "Resolved body path escaped its root." }
        return directory.resolve("${key.value}$OBJECT_SUFFIX").canonicalFile.also { file ->
            check(file.toPath().startsWith(objectDirectory.toPath())) { "Resolved body path escaped its root." }
        }
    }

    /** Rejects malformed storage keys before path resolution. */
    private fun requireValidStorageKey(key: BodyStorageKey) {
        require(STORAGE_KEY_PATTERN.matches(key.value)) { "Body storage key is invalid." }
    }

    private companion object {
        private const val INTEGRITY_BUFFER_BYTES = 64 * 1_024
        private const val OBJECT_SUFFIX = ".body"
        private val SHARD_PATTERN = Regex("[0-9a-f]{2}")
        private val STORAGE_KEY_PATTERN = Regex("[0-9a-f]{64}")
        private val OBJECT_FILE_PATTERN = Regex("[0-9a-f]{64}\\.body")
    }
}

/** Exclusive file writer used by [FileBodyStore]. */
private class FileBodyWriteSession(
    override val bodyId: BodyId,
    private val policy: BodyWritePolicy,
    private val contentEncoding: ContentEncoding?,
    private val temporary: File,
    private val destination: File,
    private val onTerminal: () -> Unit,
) : BodyWriteSession {
    private val mutex = Mutex()
    private val terminal = AtomicBoolean(false)
    private val output = FileOutputStream(temporary)
    private val digest = MessageDigest.getInstance("SHA-256")
    private var observedBytes: Long = 0L
    private var storedBytes: Long = 0L

    override suspend fun append(bytes: ByteArray): BodyAppendResult = mutex.withLock {
        check(!terminal.get()) { "Body writer is already terminal." }
        require(bytes.size <= policy.maximumChunkBytes) { "Body chunk exceeds its configured limit." }
        observedBytes += bytes.size
        val remaining = (policy.maximumStoredBytes - storedBytes).coerceAtLeast(0L)
        val accepted = minOf(bytes.size.toLong(), remaining).toInt()
        if (accepted > 0) {
            withContext(Dispatchers.IO) {
                output.write(bytes, 0, accepted)
            }
            digest.update(bytes, 0, accepted)
            storedBytes += accepted
        }
        BodyAppendResult(
            observedBytes = observedBytes,
            storedBytes = storedBytes,
            truncated = storedBytes < observedBytes,
        )
    }

    override suspend fun complete(): BodyFinalizeResult.Stored = mutex.withLock {
        check(terminal.compareAndSet(false, true)) { "Body writer is already terminal." }
        try {
            withContext(Dispatchers.IO) {
                output.flush()
                output.fd.sync()
                output.close()
                try {
                    Files.move(
                        temporary.toPath(),
                        destination.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                    )
                } catch (_: Exception) {
                    Files.move(temporary.toPath(), destination.toPath())
                }
                SessionFileSecurity.secureFile(destination)
            }
            val outcome = if (storedBytes == observedBytes) {
                BodyCaptureOutcome.Complete
            } else {
                BodyCaptureOutcome.Truncated(policy.maximumStoredBytes)
            }
            BodyFinalizeResult.Stored(
                BodyRef(
                    id = bodyId,
                    observedBytes = observedBytes,
                    storedBytes = storedBytes,
                    digest = BodyDigest(BodyDigestAlgorithm.SHA_256, digest.digest().toHex()),
                    contentEncoding = contentEncoding,
                    outcome = outcome,
                )
            )
        } finally {
            onTerminal()
        }
    }

    override suspend fun abort(outcome: BodyCaptureOutcome): BodyFinalizeResult.Unavailable = mutex.withLock {
        require(outcome is BodyCaptureOutcome.Failed || outcome is BodyCaptureOutcome.Skipped) {
            "Abort requires a failed or skipped body outcome."
        }
        check(terminal.compareAndSet(false, true)) { "Body writer is already terminal." }
        try {
            withContext(Dispatchers.IO) {
                runCatching { output.close() }
                Files.deleteIfExists(temporary.toPath())
            }
            BodyFinalizeResult.Unavailable(outcome)
        } finally {
            onTerminal()
        }
    }
}

/** Converts bytes to a fixed-width lowercase hexadecimal value. */
private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
    (byte.toInt() and 0xff).toString(16).padStart(2, '0')
}

/** Computes a SHA-256 hexadecimal value for an opaque path key. */
private fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
