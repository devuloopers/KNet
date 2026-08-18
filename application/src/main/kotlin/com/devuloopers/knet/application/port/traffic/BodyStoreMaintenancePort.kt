package com.devuloopers.knet.application.port.traffic

import com.devuloopers.knet.traffic.id.BodyId

/** Opaque stable object-store key used only for storage reconciliation. */
@JvmInline
public value class BodyStorageKey(public val value: String) {
    init {
        require(value.isNotBlank()) { "Body storage key must not be blank." }
    }
}

/**
 * Bounded finalized-object inventory page.
 *
 * @property keys Opaque keys in stable ascending order.
 * @property nextCursor Last returned key when another page exists, otherwise null.
 */
public data class BodyObjectInventoryPage(
    public val keys: List<BodyStorageKey>,
    public val nextCursor: BodyStorageKey?,
) {
    init {
        require(keys.isNotEmpty() || nextCursor == null) { "An empty inventory page cannot have a cursor." }
    }
}

/** Expected durable object metadata used by bounded background integrity verification. */
public data class BodyIntegrityExpectation(
    public val storedBytes: Long,
    public val sha256: String?,
) {
    init {
        require(storedBytes >= 0L) { "Expected body size must not be negative." }
        sha256?.let { require(it.isNotBlank()) { "Expected body digest must not be blank." } }
    }
}

/** Result of verifying one opaque finalized object without exposing its path. */
public enum class BodyIntegrityResult {
    VALID,
    MISSING,
    SIZE_MISMATCH,
    DIGEST_MISMATCH,
    DIGEST_DEFERRED_BY_BYTE_LIMIT,
}

/**
 * Maintenance-only boundary for reconciling finalized body objects with metadata ownership.
 *
 * Keys are opaque and never expose filesystem paths. Query/read consumers continue using [BodyId]
 * through [BodyAccessPort]; this port is only available to startup and retention maintenance.
 */
public interface BodyStoreMaintenancePort {
    /** Derives the stable opaque storage key for a body identifier without performing I/O. */
    public fun storageKey(bodyId: BodyId): BodyStorageKey

    /**
     * Returns one stable bounded page of finalized object keys.
     *
     * @param after Exclusive cursor returned by the previous page.
     * @param limit Maximum keys returned.
     */
    public suspend fun inventoryFinalizedObjects(
        after: BodyStorageKey?,
        limit: Int,
    ): BodyObjectInventoryPage

    /**
     * Verifies size and, within [maximumDigestBytes], SHA-256 content for one finalized object.
     * Large objects still receive an O(1) size check and are explicitly reported as deferred.
     */
    public suspend fun verifyByStorageKey(
        key: BodyStorageKey,
        expectation: BodyIntegrityExpectation,
        maximumDigestBytes: Long,
    ): BodyIntegrityResult

    /** Deletes one finalized object by its validated opaque storage key. */
    public suspend fun deleteByStorageKey(key: BodyStorageKey): BodyDeleteResult
}
