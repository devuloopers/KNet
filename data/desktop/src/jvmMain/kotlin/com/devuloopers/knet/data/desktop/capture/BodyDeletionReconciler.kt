package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.application.contract.traffic.BodyDeleteResult
import com.devuloopers.knet.application.contract.traffic.BodyStore
import com.devuloopers.knet.storage.capture.dao.CanonicalCaptureDao
import com.devuloopers.knet.traffic.id.BodyId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 * Converges canonical deletion-outbox work with the file-backed body store.
 *
 * @property dao Durable deletion work owner.
 * @property bodyStore Opaque body deletion implementation.
 */
class BodyDeletionReconciler(
    private val dao: CanonicalCaptureDao,
    private val bodyStore: BodyStore,
) {
    private val operationMutex = Mutex()

    /**
     * Processes a bounded due-only batch and acknowledges converged operations together.
     *
     * Missing files count as converged because the requested end state already exists.
     *
     * @param limit Maximum outbox rows processed in this invocation.
     * @param nowEpochMillis Due-time comparison and retry base; injectable for deterministic recovery tests.
     * @return Aggregate result without exception or filesystem details.
     */
    suspend fun reconcile(
        limit: Int = 100,
        nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
    ): BodyDeletionReconciliationResult = operationMutex.withLock {
        require(limit in 1..1_000) { "Deletion reconciliation limit must be between 1 and 1000." }
        var deleted = 0
        var alreadyMissing = 0
        var failed = 0
        val completed = ArrayList<Long>(limit)
        val retryGroups = mutableMapOf<Long, MutableList<Long>>()
        dao.getDueDeletionWork(nowEpochMillis, limit).forEach { operation ->
            try {
                when (bodyStore.delete(BodyId(operation.bodyId))) {
                    BodyDeleteResult.DELETED -> deleted += 1
                    BodyDeleteResult.NOT_FOUND -> alreadyMissing += 1
                }
                completed += operation.id
            } catch (cancelled: CancellationException) {
                // Files already removed remain in the outbox and converge as NOT_FOUND after restart.
                throw cancelled
            } catch (_: Exception) {
                failed += 1
                val retryDelay = (5_000L shl operation.attemptCount.coerceIn(0, 4)).coerceAtMost(60_000L)
                val retryAt = nowEpochMillis + retryDelay
                retryGroups.getOrPut(retryAt) { ArrayList() }.add(operation.id)
            }
        }
        // One acknowledgement per batch, not one Room transaction per file. If an acknowledgement
        // fails, replay is safe: successful filesystem deletions are idempotent NOT_FOUND results.
        if (completed.isNotEmpty()) dao.completeDeletions(completed)
        retryGroups.forEach { (retryAt, ids) -> dao.deferDeletions(ids, retryAt, DELETION_FAILURE_CODE) }
        BodyDeletionReconciliationResult(
            deleted = deleted,
            alreadyMissing = alreadyMissing,
            failed = failed,
        )
    }

    /** Earliest durable retry, or null when the worker can wait for newly committed work. */
    suspend fun nextAttemptAt(): Long? = dao.getNextDeletionAttemptAt()

    /** Reactive wakeup shared by clear, retention and startup producers, including partial commits. */
    fun observeNextAttemptAt(): kotlinx.coroutines.flow.Flow<Long?> = dao.observeNextDeletionAttemptAt()

    private companion object {
        private const val DELETION_FAILURE_CODE = "body-delete-failed"
    }
}

/**
 * Aggregate result of one bounded deletion reconciliation pass.
 *
 * @property deleted Existing body files removed.
 * @property alreadyMissing Body files that were already absent.
 * @property failed Operations retained for retry.
 */
data class BodyDeletionReconciliationResult(
    val deleted: Int,
    val alreadyMissing: Int,
    val failed: Int,
) {
    init {
        require(deleted >= 0 && alreadyMissing >= 0 && failed >= 0) {
            "Deletion reconciliation counts must not be negative."
        }
    }
}
