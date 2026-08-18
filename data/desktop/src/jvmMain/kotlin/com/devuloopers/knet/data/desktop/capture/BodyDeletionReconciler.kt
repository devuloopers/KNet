package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.application.port.traffic.BodyDeleteResult
import com.devuloopers.knet.application.port.traffic.BodyStorePort
import com.devuloopers.knet.storage.capture.dao.CanonicalCaptureDao
import com.devuloopers.knet.traffic.id.BodyId

/**
 * Converges canonical deletion-outbox work with the file-backed body store.
 *
 * @property dao Durable deletion work owner.
 * @property bodyStore Opaque body deletion implementation.
 */
class BodyDeletionReconciler(
    private val dao: CanonicalCaptureDao,
    private val bodyStore: BodyStorePort,
) {
    /**
     * Processes a bounded oldest-first batch.
     *
     * Missing files count as converged because the requested end state already exists.
     *
     * @param limit Maximum outbox rows processed in this invocation.
     * @return Aggregate result without exception or filesystem details.
     */
    suspend fun reconcile(limit: Int = 100): BodyDeletionReconciliationResult {
        require(limit in 1..1_000) { "Deletion reconciliation limit must be between 1 and 1000." }
        var deleted = 0
        var alreadyMissing = 0
        var failed = 0
        dao.getDeletionWork(limit).forEach { operation ->
            try {
                when (bodyStore.delete(BodyId(operation.bodyId))) {
                    BodyDeleteResult.DELETED -> deleted += 1
                    BodyDeleteResult.NOT_FOUND -> alreadyMissing += 1
                }
                dao.completeDeletion(operation.id)
            } catch (_: Throwable) {
                failed += 1
                dao.markDeletionFailed(operation.id, DELETION_FAILURE_CODE)
            }
        }
        return BodyDeletionReconciliationResult(
            deleted = deleted,
            alreadyMissing = alreadyMissing,
            failed = failed,
        )
    }

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
