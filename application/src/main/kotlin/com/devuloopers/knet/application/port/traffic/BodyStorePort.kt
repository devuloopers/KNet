package com.devuloopers.knet.application.port.traffic

import com.devuloopers.knet.traffic.id.BodyId
import com.devuloopers.knet.traffic.model.body.BodyCaptureOutcome
import com.devuloopers.knet.traffic.model.body.BodyRef
import com.devuloopers.knet.traffic.model.body.ContentEncoding

/**
 * Bounded policy applied to one body write.
 *
 * @property maximumStoredBytes Maximum bytes persisted before explicit truncation.
 * @property maximumChunkBytes Maximum byte-array size accepted by one append operation.
 */
public data class BodyWritePolicy(
    public val maximumStoredBytes: Long,
    public val maximumChunkBytes: Int = 1_048_576,
) {
    init {
        require(maximumStoredBytes >= 0L) { "Maximum stored body bytes must not be negative." }
        require(maximumChunkBytes in 1..1_048_576) { "Maximum body chunk must be between 1 and 1048576 bytes." }
    }
}

/**
 * Result of synchronously consuming one owned body chunk.
 *
 * @property observedBytes Total message bytes observed by this write session.
 * @property storedBytes Total bytes accepted for durable storage.
 * @property truncated Whether the per-body storage limit has been reached.
 */
public data class BodyAppendResult(
    public val observedBytes: Long,
    public val storedBytes: Long,
    public val truncated: Boolean,
)

/** Final outcome of a body write session. */
public sealed interface BodyFinalizeResult {
    /**
     * Body content was atomically finalized.
     *
     * @property body Stable reference to the stored complete or truncated representation.
     */
    public data class Stored(public val body: BodyRef) : BodyFinalizeResult

    /**
     * No readable finalized representation exists.
     *
     * @property outcome Terminal failure or skip outcome.
     */
    public data class Unavailable(public val outcome: BodyCaptureOutcome) : BodyFinalizeResult
}

/** Result of deleting one opaque body object. */
public enum class BodyDeleteResult {
    DELETED,
    NOT_FOUND,
}

/**
 * Exclusive write handle for one body object.
 *
 * Implementations consume [append] bytes before returning and never retain the caller's array.
 * Exactly one terminal method may succeed.
 */
public interface BodyWriteSession {
    /** Opaque body identifier owned by this write session. */
    public val bodyId: BodyId

    /**
     * Consumes one bounded owned chunk.
     *
     * @param bytes Bytes observed on the message; an implementation persists only its remaining budget.
     * @return Updated observed/stored counters and truncation state.
     */
    public suspend fun append(bytes: ByteArray): BodyAppendResult

    /**
     * Atomically finalizes the temporary object and digest.
     *
     * @return Complete or truncated stable body reference.
     */
    public suspend fun complete(): BodyFinalizeResult.Stored

    /**
     * Aborts and removes temporary content.
     *
     * @param outcome Failure or skip outcome explaining why no object is available.
     * @return Unavailable terminal result.
     */
    public suspend fun abort(outcome: BodyCaptureOutcome): BodyFinalizeResult.Unavailable
}

/**
 * Application port for atomic body writes, bounded range reads, and convergent deletion.
 *
 * Opaque [BodyId] values never become caller-visible filesystem paths.
 */
public interface BodyStorePort : BodyAccessPort {
    /**
     * Opens one exclusive temporary body writer.
     *
     * @param bodyId Opaque unique body identifier.
     * @param policy Per-body size/chunk bounds.
     * @param contentEncoding Optional observed representation encoding.
     * @return Exclusive write session.
     */
    public suspend fun openWrite(
        bodyId: BodyId,
        policy: BodyWritePolicy,
        contentEncoding: ContentEncoding? = null,
    ): BodyWriteSession

    /** Deletes one finalized body object by opaque identifier. */
    public suspend fun delete(bodyId: BodyId): BodyDeleteResult

    /** Removes abandoned temporary objects left by interrupted writes. */
    public suspend fun reconcileTemporaryObjects(): Int
}
