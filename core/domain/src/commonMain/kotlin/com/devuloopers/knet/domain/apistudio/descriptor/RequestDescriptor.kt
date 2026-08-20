package com.devuloopers.knet.domain.apistudio.descriptor

import com.devuloopers.knet.traffic.model.http.HttpMethod

/** Open identifier for the semantic kind of an authored API Studio request. */
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
 * Resolved semantic presentation metadata for one canonical API Studio request.
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
