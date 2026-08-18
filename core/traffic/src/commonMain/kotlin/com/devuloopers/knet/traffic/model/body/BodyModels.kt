package com.devuloopers.knet.traffic.model.body

import com.devuloopers.knet.traffic.id.BodyId

/**
 * Standard content encodings recognized by KNet.
 */
public enum class StandardContentEncoding(public val token: String) {
    IDENTITY("identity"),
    GZIP("gzip"),
    DEFLATE("deflate"),
    BROTLI("br"),
    ZSTD("zstd"),
}

/**
 * Content encoding with a typed standard set and extension-safe custom value.
 */
public sealed interface ContentEncoding {
    /** Encoding token as observed on the message. */
    public val token: String

    /**
     * Wraps a standard content encoding.
     *
     * @property value Standard encoding value.
     */
    public data class Standard(public val value: StandardContentEncoding) : ContentEncoding {
        override val token: String = value.token
    }

    /**
     * Preserves an encoding not yet modeled by KNet.
     *
     * @property token Non-blank encoding token.
     */
    public data class Custom(override val token: String) : ContentEncoding {
        init {
            require(token.isNotBlank()) { "Custom content encoding must not be blank." }
        }
    }

    public companion object {
        public val IDENTITY: ContentEncoding = Standard(StandardContentEncoding.IDENTITY)
        public val GZIP: ContentEncoding = Standard(StandardContentEncoding.GZIP)
        public val DEFLATE: ContentEncoding = Standard(StandardContentEncoding.DEFLATE)
        public val BROTLI: ContentEncoding = Standard(StandardContentEncoding.BROTLI)
        public val ZSTD: ContentEncoding = Standard(StandardContentEncoding.ZSTD)

        /**
         * Creates a typed content encoding from an observed header token.
         *
         * @param token Encoding token to normalize against the standard set.
         * @return A standard encoding when recognized, otherwise a custom value preserving [token].
         * @throws IllegalArgumentException When [token] is blank.
         */
        public fun fromToken(token: String): ContentEncoding {
            require(token.isNotBlank()) { "Content encoding must not be blank." }
            val standard = StandardContentEncoding.entries.firstOrNull {
                it.token.equals(token, ignoreCase = true)
            }
            return standard?.let(::Standard) ?: Custom(token)
        }
    }
}

/**
 * Digest algorithms supported by canonical body references.
 */
public enum class BodyDigestAlgorithm {
    SHA_256,
}

/**
 * Content digest used for integrity, deduplication, and export verification.
 *
 * @property algorithm Digest algorithm.
 * @property value Lower- or upper-case encoded digest value as produced by the storage adapter.
 */
public data class BodyDigest(
    public val algorithm: BodyDigestAlgorithm,
    public val value: String,
) {
    init {
        require(value.isNotBlank()) { "Body digest value must not be blank." }
    }
}

/**
 * Standard policy reasons for intentionally skipping body persistence.
 */
public enum class BodySkipReason {
    METADATA_ONLY_POLICY,
    CONTENT_TYPE_EXCLUDED,
    SESSION_LIMIT_REACHED,
    USER_DISABLED,
}

/**
 * Typed reasons that body capture could not complete.
 */
public sealed interface BodyFailure {
    /** Storage had no remaining capacity for this body. */
    public data object StorageFull : BodyFailure

    /** Storage rejected access because of permissions. */
    public data object PermissionDenied : BodyFailure

    /** The source stream failed before capture completed. */
    public data object SourceFailed : BodyFailure

    /**
     * Preserves an implementation-specific failure without exposing a throwable.
     *
     * @property code Stable non-blank diagnostic code safe to cross module boundaries.
     */
    public data class Custom(public val code: String) : BodyFailure {
        init {
            require(code.isNotBlank()) { "Body failure code must not be blank." }
        }
    }
}

/**
 * Terminal result of attempting to capture one request or response body.
 */
public sealed interface BodyCaptureOutcome {
    /** The complete observed body was persisted. */
    public data object Complete : BodyCaptureOutcome

    /**
     * Capture stopped after reaching an explicit limit.
     *
     * @property limitBytes Maximum persisted bytes applied to the body.
     */
    public data class Truncated(public val limitBytes: Long) : BodyCaptureOutcome {
        init {
            require(limitBytes >= 0L) { "Body truncation limit must not be negative." }
        }
    }

    /**
     * Capture was intentionally skipped by policy.
     *
     * @property reason Typed skip reason.
     */
    public data class Skipped(public val reason: BodySkipReason) : BodyCaptureOutcome

    /**
     * Capture failed unexpectedly while forwarding may have continued.
     *
     * @property reason Typed failure reason.
     */
    public data class Failed(public val reason: BodyFailure) : BodyCaptureOutcome
}

/**
 * Immutable reference to body content owned by a body-store adapter.
 *
 * @property id Opaque body identifier, never a filesystem path.
 * @property observedBytes Number of bytes observed on the proxied or executed message.
 * @property storedBytes Number of bytes made available by the body store.
 * @property digest Optional integrity digest of the stored representation.
 * @property contentEncoding Optional observed content encoding.
 * @property outcome Terminal capture outcome.
 */
public data class BodyRef(
    public val id: BodyId,
    public val observedBytes: Long,
    public val storedBytes: Long,
    public val digest: BodyDigest? = null,
    public val contentEncoding: ContentEncoding? = null,
    public val outcome: BodyCaptureOutcome,
) {
    init {
        require(observedBytes >= 0L) { "Observed body bytes must not be negative." }
        require(storedBytes >= 0L) { "Stored body bytes must not be negative." }
        require(storedBytes <= observedBytes) { "Stored body bytes must not exceed observed bytes." }
    }
}

/**
 * Body relationship carried by a shared request or response snapshot.
 *
 * Body bytes are obtained through a bounded body-access use case rather than embedded here.
 */
public sealed interface MessageBodyRef {
    /** The HTTP message has no body. */
    public data object Empty : MessageBodyRef

    /**
     * The message has body content represented by a body-store reference.
     *
     * @property body Immutable body reference.
     */
    public data class Available(public val body: BodyRef) : MessageBodyRef

    /**
     * The message had a body relationship but no readable stored content is available.
     *
     * @property outcome Skip, truncation, or failure outcome explaining availability.
     */
    public data class Unavailable(public val outcome: BodyCaptureOutcome) : MessageBodyRef
}
