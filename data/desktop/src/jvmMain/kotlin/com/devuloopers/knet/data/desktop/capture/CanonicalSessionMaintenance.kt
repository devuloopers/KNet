package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.storage.capture.dao.CanonicalCaptureDao
import com.devuloopers.knet.traffic.id.CaptureSessionId

/**
 * Coordinates crash-safe metadata and body-file maintenance for canonical sessions.
 *
 * @property dao Transactional session/deletion-outbox owner.
 * @property deletionReconciler Bounded body-file convergence worker.
 */
class CanonicalSessionMaintenance(
    private val dao: CanonicalCaptureDao,
    private val deletionReconciler: BodyDeletionReconciler,
) {
    /**
     * Clears one already-closed session without making database/file deletion atomic by assumption.
     *
     * The Room transaction first persists deletion-outbox work and removes session metadata. A
     * bounded reconciliation pass then deletes files; remaining failures stay durable for retry.
     *
     * @param sessionId Closed canonical session to clear.
     * @param requestedAtEpochMillis Wall-clock audit timestamp.
     * @param reconciliationLimit Maximum outbox operations processed immediately.
     * @return Queued and immediately reconciled counts.
     */
    suspend fun clearClosedSession(
        sessionId: CaptureSessionId,
        requestedAtEpochMillis: Long,
        reconciliationLimit: Int = 1_000,
    ): CanonicalSessionClearResult {
        require(requestedAtEpochMillis >= 0L) { "Session clear timestamp must not be negative." }
        val queued = dao.queueAndDeleteClosedSession(sessionId.value, requestedAtEpochMillis)
        val reconciliation = deletionReconciler.reconcile(reconciliationLimit)
        return CanonicalSessionClearResult(
            bodyDeletionsQueued = queued,
            bodyFilesDeleted = reconciliation.deleted,
            bodyFilesAlreadyMissing = reconciliation.alreadyMissing,
            bodyDeletionFailures = reconciliation.failed,
        )
    }
}

/**
 * Result of one crash-safe canonical session clear operation.
 *
 * @property bodyDeletionsQueued Durable outbox operations created before metadata removal.
 * @property bodyFilesDeleted Existing files removed immediately.
 * @property bodyFilesAlreadyMissing Files already in the requested deleted state.
 * @property bodyDeletionFailures Durable operations retained for retry.
 */
data class CanonicalSessionClearResult(
    val bodyDeletionsQueued: Int,
    val bodyFilesDeleted: Int,
    val bodyFilesAlreadyMissing: Int,
    val bodyDeletionFailures: Int,
) {
    init {
        require(
            bodyDeletionsQueued >= 0 &&
                bodyFilesDeleted >= 0 &&
                bodyFilesAlreadyMissing >= 0 &&
                bodyDeletionFailures >= 0
        ) { "Canonical session clear counts must not be negative." }
    }
}
