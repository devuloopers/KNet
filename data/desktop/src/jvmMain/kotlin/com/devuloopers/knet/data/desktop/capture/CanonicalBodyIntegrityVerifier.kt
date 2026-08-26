package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.application.contract.traffic.BodyIntegrityExpectation
import com.devuloopers.knet.application.contract.traffic.BodyIntegrityResult
import com.devuloopers.knet.application.contract.traffic.BodyStorageKey
import com.devuloopers.knet.application.contract.traffic.BodyStoreMaintenance
import com.devuloopers.knet.storage.capture.dao.CanonicalCaptureDao

/** Bounded background integrity scrubber for finalized canonical body objects. */
class CanonicalBodyIntegrityVerifier(
    private val dao: CanonicalCaptureDao,
    private val bodyStore: BodyStoreMaintenance,
) {
    /** Verifies at most [maximumObjects] and never hashes more than [maximumDigestBytesPerObject]. */
    suspend fun verify(
        maximumObjects: Int = 100,
        maximumDigestBytesPerObject: Long = 16L * 1_024L * 1_024L,
    ): CanonicalBodyIntegrityResult {
        require(maximumObjects in 1..1_000) { "Integrity object limit must be between 1 and 1000." }
        require(maximumDigestBytesPerObject >= 0L) { "Integrity digest byte limit must not be negative." }
        var afterBodyId: String? = null
        var checked = 0
        var valid = 0
        var missing = 0
        var corrupt = 0
        var deferred = 0
        var hasMore = false
        while (checked < maximumObjects) {
            val limit = minOf(100, maximumObjects - checked)
            val batch = dao.getFinalizedBodyRecoveryBatch(afterBodyId, limit + 1)
            val page = batch.take(limit)
            for (body in page) {
                checked += 1
                when (
                    bodyStore.verifyByStorageKey(
                        key = BodyStorageKey(body.storageKey),
                        expectation = BodyIntegrityExpectation(body.storedBytes, body.digestValue),
                        maximumDigestBytes = maximumDigestBytesPerObject,
                    )
                ) {
                    BodyIntegrityResult.VALID -> valid += 1
                    BodyIntegrityResult.MISSING -> missing += dao.markBodyMissing(body.id)
                    BodyIntegrityResult.SIZE_MISMATCH -> {
                        corrupt += dao.markBodyCorrupt(body.id, "FAILED:body-integrity-size-mismatch")
                    }

                    BodyIntegrityResult.DIGEST_MISMATCH -> {
                        corrupt += dao.markBodyCorrupt(body.id, "FAILED:body-integrity-digest-mismatch")
                    }

                    BodyIntegrityResult.DIGEST_DEFERRED_BY_BYTE_LIMIT -> deferred += 1
                }
            }
            hasMore = batch.size > limit
            afterBodyId = page.lastOrNull()?.id
            if (page.isEmpty() || !hasMore) break
        }
        return CanonicalBodyIntegrityResult(checked, valid, missing, corrupt, deferred, hasMore)
    }
}

/** Aggregate result of one bounded integrity pass. */
data class CanonicalBodyIntegrityResult(
    val checked: Int,
    val valid: Int,
    val missing: Int,
    val corrupt: Int,
    val digestDeferred: Int,
    val hasMore: Boolean,
)
