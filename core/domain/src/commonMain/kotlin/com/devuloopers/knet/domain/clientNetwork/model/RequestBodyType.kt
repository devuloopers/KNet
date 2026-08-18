package com.devuloopers.knet.domain.clientNetwork.model

/**
 * Strongly-typed domain representation of supported request body payload formats.
 */
enum class RequestBodyType {
    NONE,
    JSON,
    XML,
    FORM_DATA,
    X_WWW_FORM_URLENCODED,
    MULTIPART,
    GRAPHQL,
    RAW_TEXT
}

/** One ordered form field used by URL-encoded and multipart request bodies. */
data class RequestFormField(
    val name: String,
    val value: String,
) {
    init {
        require(name.isNotBlank()) { "Request form field name must not be blank." }
    }
}

/**
 * Canonical outbound request body consumed by HTTP execution adapters.
 *
 * A body variant owns all data required to encode itself. This prevents callers from passing a
 * body kind, text payload, and unrelated form-parameter map that can disagree at runtime.
 */
sealed interface OutboundRequestBody {
    /** Request has no entity body. */
    data object None : OutboundRequestBody

    /** JSON text encoded using `application/json`. */
    data class Json(val content: String) : OutboundRequestBody

    /** XML text encoded using `application/xml`. */
    data class Xml(val content: String) : OutboundRequestBody

    /** Plain or custom text with an explicit media type. */
    data class Text(
        val content: String,
        val mediaType: String = "text/plain",
    ) : OutboundRequestBody {
        init {
            require(mediaType.isNotBlank()) { "Request body media type must not be blank." }
        }
    }

    /** Ordered `application/x-www-form-urlencoded` fields. */
    data class FormUrlEncoded(
        val fields: List<RequestFormField>,
    ) : OutboundRequestBody

    /** Ordered textual multipart form fields. Binary parts can be added as another part subtype. */
    data class Multipart(
        val fields: List<RequestFormField>,
    ) : OutboundRequestBody

    /** GraphQL HTTP payload text; the adapter normalizes a raw query into its JSON envelope. */
    data class GraphQl(val content: String) : OutboundRequestBody
}
