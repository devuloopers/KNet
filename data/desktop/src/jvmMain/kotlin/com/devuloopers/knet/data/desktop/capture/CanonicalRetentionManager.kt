package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.storage.capture.dao.CanonicalCaptureDao
import com.devuloopers.knet.traffic.id.CaptureSessionId

/**
 * Global terminal-session retention policy enforced in bounded oldest-first passes.
 *
 * @property maximumClosedSessions Maximum terminal sessions retained after a converged pass.
 * @property maximumStoredBytes Maximum finalized body bytes retained across terminal sessions.
 * @property maximumSessionsPerRun Maximum sessions queued for removal in one invocation.
 * @property deletionReconciliationLimit Maximum body deletion operations processed immediately.
 */
data class CanonicalRetentionPolicy(
    val maximumClosedSessions: Int,
    val maximumStoredBytes: Long,
    val maximumSessionsPerRun: Int = 10,
    val deletionReconciliationLimit: Int = 1_000,
) {
    init {
        require(maximumClosedSessions >= 0) { "Maximum retained session count must not be negative." }
        require(maximumStoredBytes >= 0L) { "Maximum retained body bytes must not be negative." }
        require(maximumSessionsPerRun in 1..100) { "Retention session batch must be between 1 and 100." }
        require(deletionReconciliationLimit in 1..1_000) {
            "Retention deletion reconciliation limit must be between 1 and 1000."
        }
    }
}

/**
 * Applies [CanonicalRetentionPolicy] without loading exchanges or bodies into memory.
 *
 * @property dao Canonical session and aggregate-query owner.
 * @property deletionReconciler Durable body-file deletion worker.
 */
class CanonicalRetentionManager(
    private val dao: CanonicalCaptureDao,
    private val deletionReconciler: BodyDeletionReconciler,
) {
    /**
     * Queues oldest terminal sessions until count/byte targets converge or the run limit is reached.
     *
     * Active sessions are excluded by every DAO query and can never be evicted by retention.
     *
     * @param policy Count, byte, and bounded-work limits.
     * @param requestedAtEpochMillis Wall-clock audit timestamp for durable deletion work.
     * @return Aggregate eviction and reconciliation outcome.
     */
    suspend fun enforce(
        policy: CanonicalRetentionPolicy,
        requestedAtEpochMillis: Long,
    ): CanonicalRetentionResult {
        require(requestedAtEpochMillis >= 0L) { "Retention timestamp must not be negative." }
        var closedSessions = dao.countClosedSessions()
        var storedBytes = dao.sumClosedSessionStoredBytes()
        val candidates = dao.getOldestClosedSessionSummaries(policy.maximumSessionsPerRun)
        val evicted = ArrayList<CaptureSessionId>(policy.maximumSessionsPerRun)
        var queuedBodyDeletions = 0

        for (candidate in candidates) {
            if (closedSessions <= policy.maximumClosedSessions && storedBytes <= policy.maximumStoredBytes) break
            queuedBodyDeletions += dao.queueAndDeleteClosedSession(candidate.sessionId, requestedAtEpochMillis)
            evicted += CaptureSessionId(candidate.sessionId)
            closedSessions -= 1
            storedBytes = (storedBytes - candidate.storedBytes).coerceAtLeast(0L)
        }

        val deletionResult = deletionReconciler.reconcile(policy.deletionReconciliationLimit)
        return CanonicalRetentionResult(
            evictedSessions = evicted,
            queuedBodyDeletions = queuedBodyDeletions,
            deletionResult = deletionResult,
            remainingClosedSessions = closedSessions,
            remainingStoredBytes = storedBytes,
            requiresAnotherPass =
                closedSessions > policy.maximumClosedSessions || storedBytes > policy.maximumStoredBytes,
        )
    }
}

/**
 * Outcome of one bounded canonical retention pass.
 *
 * @property evictedSessions Oldest terminal sessions removed in this pass.
 * @property queuedBodyDeletions Durable body deletion operations created before metadata removal.
 * @property deletionResult Body-file work reconciled immediately.
 * @property remainingClosedSessions Terminal session count after metadata eviction.
 * @property remainingStoredBytes Terminal-session body bytes after metadata eviction.
 * @property requiresAnotherPass Whether configured limits still require bounded follow-up work.
 */
data class CanonicalRetentionResult(
    val evictedSessions: List<CaptureSessionId>,
    val queuedBodyDeletions: Int,
    val deletionResult: BodyDeletionReconciliationResult,
    val remainingClosedSessions: Int,
    val remainingStoredBytes: Long,
    val requiresAnotherPass: Boolean,
) {
    init {
        require(queuedBodyDeletions >= 0 && remainingClosedSessions >= 0 && remainingStoredBytes >= 0L) {
            "Canonical retention counts must not be negative."
        }
    }
}
