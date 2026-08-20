package com.devuloopers.knet.domain.request.descriptor

import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.HeaderField

/** Open identifier for a semantic request kind shared by authored and captured workflows. */
@JvmInline
value class RequestKindId(val value: String) {
    init {
        require(value.isNotBlank()) { "Request kind ID must not be blank." }
        require(value == value.trim().lowercase()) {
            "Request kind ID must be a normalized lowercase token."
        }
    }

    companion object {
        /** Ordinary HTTP request kind. */
        val HTTP: RequestKindId = RequestKindId("http")

        /** GraphQL request kind carried over an HTTP transport. */
        val GRAPHQL: RequestKindId = RequestKindId("graphql")
    }
}

/**
 * Immutable bounded request body offered to semantic request descriptor strategies.
 *
 * The wrapper owns a defensive copy so descriptor implementations cannot retain or mutate bytes
 * owned by API Studio, capture storage, or a suspended breakpoint.
 */
class RequestDescriptorBody(bytes: ByteArray) {
    private val content = bytes.copyOf()

    init {
        require(content.size <= MAXIMUM_BYTES) { "Request descriptor body exceeds its inspection limit." }
    }

    /** Number of bytes owned by this descriptor body. */
    val size: Int
        get() = content.size

    /** Returns an independent byte copy for a binary-aware descriptor strategy. */
    fun copyBytes(): ByteArray = content.copyOf()

    /** Decodes the bounded body as UTF-8 text for textual protocol strategies. */
    fun decodeToString(): String = content.decodeToString()

    override fun equals(other: Any?): Boolean =
        other is RequestDescriptorBody && content.contentEquals(other.content)

    override fun hashCode(): Int = content.contentHashCode()

    override fun toString(): String = "RequestDescriptorBody(size=$size)"

    companion object {
        /** Maximum body prefix a request descriptor may own. */
        const val MAXIMUM_BYTES: Int = 1_048_576
    }
}

/**
 * Protocol-neutral request input shared by authored, captured, and pending request presentation.
 *
 * @property transportMethod Actual HTTP transport method.
 * @property absoluteUrl Absolute or user-authored request target used for fallback naming and metadata detection.
 * @property headers Ordered canonical headers. Repeated header fields remain independent.
 * @property body Optional bounded body owned by this input.
 * @property bodyComplete Whether [body] contains the complete request body.
 * @property semanticKindHint Optional trusted kind supplied by an extension rule or persisted semantic annotation.
 */
data class RequestDescriptorInput(
    val transportMethod: HttpMethod,
    val absoluteUrl: String,
    val headers: List<HeaderField> = emptyList(),
    val body: RequestDescriptorBody? = null,
    val bodyComplete: Boolean = body != null,
    val semanticKindHint: RequestKindId? = null,
)

/**
 * Resolved semantic presentation metadata for one canonical authored or captured request.
 *
 * @property suggestedName Generated request/session title.
 * @property kind Open semantic kind used for feature styling and filtering.
 * @property badgeLabel Compact sidebar identity such as `POST`, `GQL`, or `WS`.
 * @property transportMethod Actual HTTP transport method, retained even when the badge represents a protocol.
 */
data class RequestDescriptor(
    val suggestedName: String,
    val kind: RequestKindId,
    val badgeLabel: String,
    val transportMethod: HttpMethod
)

/** Optional partial result emitted by one ordered [RequestDescriptorStrategy]. */
data class RequestDescriptorContribution(
    val kind: RequestKindId,
    val badgeLabel: String,
    val suggestedName: String? = null
) {
    init {
        require(badgeLabel.isNotBlank()) { "Request badge label must not be blank." }
    }
}
