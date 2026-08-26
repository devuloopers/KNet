package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.application.contract.traffic.BodyRange
import com.devuloopers.knet.application.contract.traffic.BodyDeleteResult
import com.devuloopers.knet.application.contract.traffic.BodyStorageKey
import com.devuloopers.knet.application.contract.traffic.BodyStore
import com.devuloopers.knet.application.contract.traffic.BodyStoreMaintenance
import com.devuloopers.knet.storage.capture.dao.CanonicalCaptureDao
import com.devuloopers.knet.traffic.id.BodyId

/**
 * Performs bounded canonical metadata/body convergence before a new capture writer opens.
 *
 * @property dao Canonical lifecycle and body metadata owner.
 * @property bodyStore Opaque body store verified through its bounded read contract.
 * @property bodyStoreMaintenance Opaque finalized-object inventory and deletion boundary.
 * @property deletionReconciler Durable deletion-outbox worker.
 */
class CanonicalStartupRecovery(
    private val dao: CanonicalCaptureDao,
    private val bodyStore: BodyStore,
    private val bodyStoreMaintenance: BodyStoreMaintenance,
    private val deletionReconciler: BodyDeletionReconciler = BodyDeletionReconciler(dao, bodyStore),
) {
    /**
     * Recovers interrupted sessions, temporary objects, pending deletions, and missing body metadata.
     *
     * Finalized bodies are inspected in keyset batches and capped by [maximumBodiesToCheck], so
     * startup never materializes every body row. A returned `bodyScanHasMore` flag schedules a later
     * maintenance pass without delaying proxy startup indefinitely.
     *
     * @param recoveredAtEpochMillis Wall-clock recovery timestamp.
     * @param bodyBatchSize Number of finalized body rows read per keyset query.
     * @param maximumBodiesToCheck Maximum body objects verified during this invocation.
     * @param deletionLimit Maximum deletion-outbox rows reconciled during this invocation.
     * @param maximumStoredObjectsToCheck Maximum finalized files checked for metadata ownership.
     * @return Aggregate bounded recovery result.
     */
    suspend fun recover(
        recoveredAtEpochMillis: Long,
        bodyBatchSize: Int = 250,
        maximumBodiesToCheck: Int = 1_000,
        deletionLimit: Int = 250,
        maximumStoredObjectsToCheck: Int = 10_000,
    ): CanonicalStartupRecoveryResult {
        require(recoveredAtEpochMillis >= 0L) { "Recovery timestamp must not be negative." }
        require(bodyBatchSize in 1..1_000) { "Recovery body batch must be between 1 and 1000." }
        require(maximumBodiesToCheck in 1..10_000) { "Recovery body check limit must be between 1 and 10000." }
        require(deletionLimit in 1..1_000) { "Recovery deletion limit must be between 1 and 1000." }
        require(maximumStoredObjectsToCheck in 1..100_000) {
            "Recovery finalized-object limit must be between 1 and 100000."
        }

        val recoveredMessages = dao.recoverInterruptedDuplexMessages()
        val recoveredExchanges = dao.recoverInterruptedExchanges(recoveredAtEpochMillis)
        val recoveredConnections = dao.recoverInterruptedConnections(recoveredAtEpochMillis)
        val recoveredSessions = dao.recoverInterruptedSessions(recoveredAtEpochMillis)
        val temporaryObjectsDeleted = bodyStore.reconcileTemporaryObjects()
        val deletionResult = deletionReconciler.reconcile(deletionLimit)
        val orphanReconciliation = reconcileOrphanedFinalizedObjects(
            batchSize = bodyBatchSize,
            maximumObjects = maximumStoredObjectsToCheck,
        )
        var afterBodyId: String? = null
        var checkedBodies = 0
        var missingBodies = 0
        var failedBodyChecks = 0
        var hasMore = false

        while (checkedBodies < maximumBodiesToCheck) {
            val remaining = maximumBodiesToCheck - checkedBodies
            val pageLimit = minOf(bodyBatchSize, remaining)
            val batch = dao.getFinalizedBodyRecoveryBatch(afterBodyId, pageLimit + 1)
            val page = batch.take(pageLimit)
            page.forEach { body ->
                checkedBodies += 1
                when (bodyAvailability(BodyId(body.id))) {
                    BodyAvailability.AVAILABLE -> Unit
                    BodyAvailability.MISSING -> {
                        missingBodies += dao.markBodyMissing(body.id)
                    }
                    BodyAvailability.CHECK_FAILED -> failedBodyChecks += 1
                }
            }
            hasMore = batch.size > pageLimit
            afterBodyId = page.lastOrNull()?.id
            if (page.isEmpty() || !hasMore) break
        }

        return CanonicalStartupRecoveryResult(
            recoveredSessions = recoveredSessions,
            recoveredConnections = recoveredConnections,
            recoveredExchanges = recoveredExchanges,
            recoveredMessages = recoveredMessages,
            temporaryObjectsDeleted = temporaryObjectsDeleted,
            deletionResult = deletionResult,
            checkedBodies = checkedBodies,
            missingBodies = missingBodies,
            failedBodyChecks = failedBodyChecks,
            bodyScanHasMore = hasMore,
            checkedStoredObjects = orphanReconciliation.checked,
            orphanedStoredObjectsDeleted = orphanReconciliation.deleted,
            failedStoredObjectDeletes = orphanReconciliation.failedDeletes,
            storedObjectScanHasMore = orphanReconciliation.scanHasMore,
        )
    }

    /** Deletes finalized objects absent from canonical metadata using bounded opaque-key pages. */
    private suspend fun reconcileOrphanedFinalizedObjects(
        batchSize: Int,
        maximumObjects: Int,
    ): OrphanReconciliationResult {
        var cursor: BodyStorageKey? = null
        var checked = 0
        var deleted = 0
        var failedDeletes = 0
        var hasMore = false
        while (checked < maximumObjects) {
            val page = bodyStoreMaintenance.inventoryFinalizedObjects(
                after = cursor,
                limit = minOf(batchSize, maximumObjects - checked),
            )
            if (page.keys.isEmpty()) {
                hasMore = false
                break
            }
            val existing = dao.getExistingStorageKeys(page.keys.map { key -> key.value }).toHashSet()
            page.keys.forEach { key ->
                checked += 1
                if (key.value !in existing) {
                    try {
                        if (bodyStoreMaintenance.deleteByStorageKey(key) == BodyDeleteResult.DELETED) {
                            deleted += 1
                        }
                    } catch (_: Throwable) {
                        failedDeletes += 1
                    }
                }
            }
            cursor = page.nextCursor
            hasMore = cursor != null
            if (!hasMore) break
        }
        return OrphanReconciliationResult(checked, deleted, failedDeletes, hasMore)
    }

    /** Distinguishes an absent body from a storage adapter failure. */
    private suspend fun bodyAvailability(bodyId: BodyId): BodyAvailability = try {
        bodyStore.readBody(bodyId, BodyRange(offset = 0L, length = 1))
        BodyAvailability.AVAILABLE
    } catch (_: IllegalStateException) {
        BodyAvailability.MISSING
    } catch (_: Throwable) {
        BodyAvailability.CHECK_FAILED
    }

    /** Internal body verification outcome. */
    private enum class BodyAvailability {
        AVAILABLE,
        MISSING,
        CHECK_FAILED,
    }
}

private data class OrphanReconciliationResult(
    val checked: Int = 0,
    val deleted: Int = 0,
    val failedDeletes: Int = 0,
    val scanHasMore: Boolean,
)

/**
 * Aggregate outcome of one bounded startup recovery pass.
 *
 * @property recoveredSessions Interrupted active sessions transitioned to terminal recovery state.
 * @property recoveredConnections Interrupted open connections transitioned to terminal state.
 * @property recoveredExchanges Interrupted exchanges transitioned to failed terminal state.
 * @property recoveredMessages Interrupted framed messages transitioned to failed terminal state.
 * @property temporaryObjectsDeleted Abandoned temporary body files removed.
 * @property deletionResult Durable deletion work processed.
 * @property checkedBodies Finalized body objects verified.
 * @property missingBodies Missing body metadata rows marked unavailable.
 * @property failedBodyChecks Storage checks that failed without being classified as missing.
 * @property bodyScanHasMore Whether a later bounded pass is required.
 * @property checkedStoredObjects Finalized objects checked for metadata ownership.
 * @property orphanedStoredObjectsDeleted Finalized objects removed because no metadata owns them.
 * @property failedStoredObjectDeletes Orphan deletions that failed and remain retryable.
 * @property storedObjectScanHasMore Whether a later bounded object-inventory pass is required.
 */
data class CanonicalStartupRecoveryResult(
    val recoveredSessions: Int,
    val recoveredConnections: Int,
    val recoveredExchanges: Int,
    val recoveredMessages: Int,
    val temporaryObjectsDeleted: Int,
    val deletionResult: BodyDeletionReconciliationResult,
    val checkedBodies: Int,
    val missingBodies: Int,
    val failedBodyChecks: Int,
    val bodyScanHasMore: Boolean,
    val checkedStoredObjects: Int,
    val orphanedStoredObjectsDeleted: Int,
    val failedStoredObjectDeletes: Int,
    val storedObjectScanHasMore: Boolean,
) {
    init {
        require(
            recoveredSessions >= 0 &&
                recoveredConnections >= 0 &&
                recoveredExchanges >= 0 &&
                recoveredMessages >= 0 &&
                temporaryObjectsDeleted >= 0 &&
                checkedBodies >= 0 &&
                missingBodies >= 0 &&
                failedBodyChecks >= 0 &&
                checkedStoredObjects >= 0 &&
                orphanedStoredObjectsDeleted >= 0 &&
                failedStoredObjectDeletes >= 0
        ) { "Canonical startup recovery counts must not be negative." }
    }
}
