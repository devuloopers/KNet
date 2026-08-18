package com.devuloopers.knet.storage.capture.model

/**
 * Bounded retention projection for one terminal canonical capture session.
 *
 * @property sessionId Stable session identifier.
 * @property startedAtEpochMillis Session age used for oldest-first eviction.
 * @property storedBytes Sum of finalized body bytes attributed to the session.
 */
data class CanonicalSessionStorageSummary(
    val sessionId: String,
    val startedAtEpochMillis: Long,
    val storedBytes: Long,
)
