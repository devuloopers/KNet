package com.devuloopers.knet.application.contract.traffic

import com.devuloopers.knet.traffic.id.BodyId

/**
 * Bounded body range requested by an authorized feature.
 *
 * @property offset Zero-based byte offset.
 * @property length Maximum bytes to return.
 */
public data class BodyRange(
    public val offset: Long,
    public val length: Int,
) {
    init {
        require(offset >= 0L) { "Body range offset must not be negative." }
        require(length in 1..1_048_576) { "Body range length must be between 1 and 1048576 bytes." }
    }
}

/**
 * Immutable-copy body range returned to an application feature.
 *
 * The constructor and [copyBytes] defensively copy content so callers cannot mutate a shared
 * storage or cache buffer.
 *
 * @param bytes Bounded body bytes returned by the storage adapter.
 * @property offset Source body offset of the first byte.
 * @property endOfBody Whether the returned range reaches the available stored body end.
 */
public class BodyChunk(
    bytes: ByteArray,
    public val offset: Long,
    public val endOfBody: Boolean,
) {
    private val content: ByteArray = bytes.copyOf()

    init {
        require(offset >= 0L) { "Body chunk offset must not be negative." }
        require(content.size <= 1_048_576) { "Body chunk exceeds the application range limit." }
    }

    /** Number of bytes in this bounded chunk. */
    public val size: Int
        get() = content.size

    /**
     * Returns a defensive copy of the chunk content.
     *
     * @return Independent byte array owned by the caller.
     */
    public fun copyBytes(): ByteArray = content.copyOf()

    public override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BodyChunk) return false
        return offset == other.offset && endOfBody == other.endOfBody && content.contentEquals(other.content)
    }

    public override fun hashCode(): Int {
        var result = content.contentHashCode()
        result = 31 * result + offset.hashCode()
        result = 31 * result + endOfBody.hashCode()
        return result
    }

    public override fun toString(): String = "BodyChunk(size=$size, offset=$offset, endOfBody=$endOfBody)"
}

/** Application contract for bounded access to body content owned by a storage adapter. */
public interface BodyAccess {
    /**
     * Reads one bounded range from body storage.
     *
     * @param bodyId Opaque body identifier.
     * @param range Bounded range request.
     * @return Immutable-copy body chunk.
     * @throws IllegalStateException When the body is missing or unavailable.
     */
    public suspend fun readBody(bodyId: BodyId, range: BodyRange): BodyChunk
}
