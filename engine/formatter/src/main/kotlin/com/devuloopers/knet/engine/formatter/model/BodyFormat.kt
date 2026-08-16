package com.devuloopers.knet.engine.formatter.model

/**
 * Strongly-typed representation of an HTTP payload body format result.
 *
 * Implemented as a sealed interface defining domain characteristics of detected payload formats.
 * Variants containing single-document text payloads implement [HasTextContent] for clean capability polymorphism.
 */
sealed interface BodyFormat {
    /**
     * Short human-readable badge label suitable for UI rendering (e.g. "JSON", "XML", "HTML").
     */
    val badgeLabel: String

    /**
     * Capability interface implemented by [BodyFormat] variants whose payload represents a single,
     * fully resolved and formatted text document suitable for code editors.
     */
    interface HasTextContent {
        /**
         * Resolved and formatted text payload.
         */
        val textContent: String
    }

    /**
     * Pretty-printed JSON object or array.
     *
     * @property formattedText Pretty-printed JSON text.
     */
    data class Json(val formattedText: String) : BodyFormat, HasTextContent {
        override val badgeLabel: String = "JSON"
        override val textContent: String get() = formattedText
    }

    /**
     * Multi-frame JSON stream (e.g. NDJSON or JSON lines).
     *
     * @property frames List of individual raw JSON frame strings.
     */
    data class JsonStream(val frames: List<String>) : BodyFormat {
        override val badgeLabel: String = "JSON Stream"
    }

    /**
     * URL-encoded or multipart form key-value pairs.
     *
     * @property pairs List of parsed form key-value pairs.
     */
    data class FormData(val pairs: List<Pair<String, String>>) : BodyFormat {
        override val badgeLabel: String = "Form Data"
    }

    /**
     * Server-Sent Events stream event lines.
     *
     * @property events List of parsed SSE event strings.
     */
    data class SseStream(val events: List<String>) : BodyFormat {
        override val badgeLabel: String = "SSE Stream"
    }

    /**
     * Protobuf descriptor string (schema-based or raw wire decoded).
     *
     * @property descriptor Formatted protobuf schema descriptor or field dump.
     */
    data class Protobuf(val descriptor: String) : BodyFormat, HasTextContent {
        override val badgeLabel: String = "Protobuf"
        override val textContent: String get() = descriptor
    }

    /**
     * Image MIME label (PNG Image, JPEG Image, etc.).
     *
     * @property label Human-readable image descriptor label.
     */
    data class Image(val label: String) : BodyFormat {
        override val badgeLabel: String get() = label
    }

    /**
     * Formatted HTML markup.
     *
     * @property formattedText Pretty-printed HTML markup text.
     */
    data class Html(val formattedText: String) : BodyFormat, HasTextContent {
        override val badgeLabel: String = "HTML"
        override val textContent: String get() = formattedText
    }

    /**
     * Formatted XML markup.
     *
     * @property formattedText Pretty-printed XML document text.
     */
    data class Xml(val formattedText: String) : BodyFormat, HasTextContent {
        override val badgeLabel: String = "XML"
        override val textContent: String get() = formattedText
    }

    /**
     * Decoded CBOR payload as pretty-printed JSON string.
     *
     * @property formattedText Pretty-printed JSON representation of CBOR payload.
     */
    data class Cbor(val formattedText: String) : BodyFormat, HasTextContent {
        override val badgeLabel: String = "CBOR"
        override val textContent: String get() = formattedText
    }

    /**
     * Formatted JavaScript source code.
     *
     * @property formattedText Formatted JavaScript source text.
     */
    data class Js(val formattedText: String) : BodyFormat, HasTextContent {
        override val badgeLabel: String = "JS"
        override val textContent: String get() = formattedText
    }

    /**
     * Formatted CSS stylesheet.
     *
     * @property formattedText Formatted CSS stylesheet text.
     */
    data class Css(val formattedText: String) : BodyFormat, HasTextContent {
        override val badgeLabel: String = "CSS"
        override val textContent: String get() = formattedText
    }

    /**
     * Parsed gRPC-Web binary frames.
     *
     * @property frames List of parsed gRPC-Web frames.
     */
    data class GrpcWeb(val frames: List<Frame>) : BodyFormat {
        override val badgeLabel: String = "gRPC-Web"

        /**
         * Represents a single parsed gRPC-Web binary frame.
         *
         * @property isTrailer True if this is a trailer frame (metadata/headers).
         * @property payloadHex Hexadecimal string representation of the raw frame bytes.
         * @property decodedJsonOrText Human-readable decoded payload content.
         */
        data class Frame(
            val isTrailer: Boolean,
            val payloadHex: String,
            val decodedJsonOrText: String
        )
    }

    /**
     * GraphQL operation type, name, query text, variables JSON, and extensions JSON.
     *
     * @property operationType Operation type (e.g., "query", "mutation", "subscription").
     * @property operationName Optional operation name from query header.
     * @property queryText Pristine GraphQL query document text.
     * @property variablesJson Variables payload as JSON string.
     * @property extensionsJson Optional extensions payload as JSON string.
     */
    data class GraphQL(
        val operationType: String,
        val operationName: String?,
        val queryText: String,
        val variablesJson: String,
        val extensionsJson: String = ""
    ) : BodyFormat, HasTextContent {
        override val badgeLabel: String
            get() = if (!operationName.isNullOrEmpty()) "GQL: $operationName" else "GQL: $operationType"
        override val textContent: String get() = queryText
    }

    /**
     * Raw plain text fallback.
     *
     * @property text Raw unformatted plain text.
     */
    data class RawText(val text: String) : BodyFormat, HasTextContent {
        override val badgeLabel: String = "PLAIN"
        override val textContent: String get() = text
    }
}
