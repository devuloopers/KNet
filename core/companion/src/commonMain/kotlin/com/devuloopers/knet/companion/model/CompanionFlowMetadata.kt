package com.devuloopers.knet.companion.model

import kotlin.io.encoding.Base64

/**
 * Trusted per-flow metadata produced by a platform packet adapter.
 *
 * The value is accepted by Desktop only after companion authentication. An application identity
 * must therefore be omitted unless the platform resolved it from an original transport tuple.
 *
 * @property verifiedSourceApplicationId Platform package or bundle identity verified by the OS.
 */
public data class CompanionFlowMetadata(
    public val verifiedSourceApplicationId: String,
) {
    init {
        require(verifiedSourceApplicationId.isNotBlank()) { "Source application identity must not be blank." }
        require(verifiedSourceApplicationId.length <= MAXIMUM_SOURCE_APPLICATION_CHARACTERS) {
            "Source application identity is too long."
        }
        require(verifiedSourceApplicationId.none { it.isISOControl() }) {
            "Source application identity must not contain control characters."
        }
    }

    private companion object {
        const val MAXIMUM_SOURCE_APPLICATION_CHARACTERS: Int = 255
    }
}

/** Strict codec for the authenticated companion flow-metadata header. */
public object CompanionFlowMetadataCodec {
    private const val VERSION_PREFIX: String = "v1."
    private const val MAXIMUM_ENCODED_CHARACTERS: Int = 1_024
    private val encoding: Base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    /** Encodes [metadata] without whitespace or header delimiters. */
    public fun encode(metadata: CompanionFlowMetadata): String =
        VERSION_PREFIX + encoding.encode(metadata.verifiedSourceApplicationId.encodeToByteArray())

    /** Decodes one supported bounded value, or returns `null` for malformed/future input. */
    public fun decode(value: String): CompanionFlowMetadata? {
        if (value.length !in VERSION_PREFIX.length + 1..MAXIMUM_ENCODED_CHARACTERS ||
            !value.startsWith(VERSION_PREFIX)
        ) {
            return null
        }
        val decoded = runCatching {
            encoding.decode(value.removePrefix(VERSION_PREFIX)).decodeToString(throwOnInvalidSequence = true)
        }.getOrNull() ?: return null
        return runCatching { CompanionFlowMetadata(decoded) }.getOrNull()
    }
}
