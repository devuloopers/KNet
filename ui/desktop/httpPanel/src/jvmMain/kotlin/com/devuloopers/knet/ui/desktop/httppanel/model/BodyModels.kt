package com.devuloopers.knet.ui.desktop.httppanel.model

import com.devuloopers.knet.engine.formatter.model.BodyFormat
import com.devuloopers.knet.engine.formatter.registry.BodyFormatterRegistry
import com.devuloopers.knet.ui.core.components.keyvalue.KeyValueEntry
import com.devuloopers.knet.ui.desktop.httppanel.mapper.GraphQlPayloadMapper

/**
 * Strongly-typed body payload mode for HTTP request authoring in API Studio and Request Interceptors.
 *
 * @property label User-facing display label for the mode pill selector.
 * @property tabLabel Short dynamic label used in the Body sub-tab header (e.g. "Body (JSON)").
 */
enum class RequestBodyMode(val label: String, val tabLabel: String) {
    NONE("none", "Body"),
    JSON("json", "Body (JSON)"),
    FORM_DATA("form-data", "Body (form-data)"),
    X_WWW_FORM_URLENCODED("x-www-form-urlencoded", "Body (url-encoded)"),
    RAW("raw", "Body (raw)"),
    GRAPHQL("graphql", "Body (GraphQL)")
}

/**
 * Strongly-typed body payload mode for HTTP response inspection and live editing.
 *
 * Excludes client-only upload encodings (form-data, x-www-form-urlencoded, graphql query syntax)
 * and focuses on universal server response data formats.
 *
 * @property label User-facing display label for the response mode pill selector.
 */
enum class ResponseBodyMode(val label: String) {
    NONE("none"),
    JSON("json"),
    XML("xml"),
    HTML("html"),
    TEXT("text"),
    RAW("raw")
}

/**
 * Strongly-typed raw body sub-format selector for the [RequestBodyMode.RAW] payload mode.
 *
 * Each variant controls the syntax highlighting language hint passed to [KNetCodeEditor]
 * and the MIME Content-Type that should be set on the request.
 *
 * @property label User-facing display label for the format dropdown.
 * @property languageHint Language token forwarded to [KNetCodeEditor] for syntax highlighting.
 * @property contentType HTTP Content-Type MIME type produced by this raw sub-format.
 */
enum class RawSubFormat(
    val label: String,
    val languageHint: String,
    val contentType: String
) {
    TEXT("Text", "plain", "text/plain"),
    JSON("JSON", "json", "application/json"),
    XML("XML", "xml", "application/xml"),
    HTML("HTML", "html", "text/html"),
    JAVASCRIPT("JavaScript", "javascript", "text/javascript")
}

/**
 * Strongly-typed sub-tabs for structured GraphQL request authoring.
 *
 * @property label User-facing display label for the sub-tab chip.
 */
enum class GraphQlSubTab(val label: String) {
    QUERY("Query"),
    VARIABLES("Variables (JSON)"),
    EXTENSIONS("Extensions (JSON)")
}

/**
 * Immutable state model holding structured GraphQL request components.
 *
 * @property queryText Raw GraphQL query or mutation document syntax (clean unescaped text).
 * @property variablesText Pretty-printed JSON for GraphQL `$variables`.
 * @property operationName Optional name of the active GraphQL operation to execute.
 * @property extensionsText Pretty-printed JSON for GraphQL `$extensions`.
 * @property activeSubTab Currently selected GraphQL editor sub-tab ([GraphQlSubTab]).
 */
data class GraphQlState(
    val queryText: String = "",
    val variablesText: String = DEFAULT_JSON_OBJECT_PLACEHOLDER,
    val operationName: String = "",
    val extensionsText: String = DEFAULT_JSON_OBJECT_PLACEHOLDER,
    val activeSubTab: GraphQlSubTab = GraphQlSubTab.QUERY
) {
    companion object {
        const val DEFAULT_JSON_OBJECT_PLACEHOLDER: String = "{\n  \n}"
    }
}

/**
 * Immutable DTO holding the complete Body payload editor configuration for a single HTTP Request.
 *
 * @property mode Active body payload mode for requests (None, JSON, form-data, url-encoded, raw, GraphQL).
 * @property rawSubFormat Active raw sub-format selector when [mode] is [RequestBodyMode.RAW].
 * @property payloadText Raw text/JSON/XML/HTML/JS/GraphQL payload content for text-based modes.
 * @property formDataEntries Key-value entries used for [RequestBodyMode.FORM_DATA] multipart payloads.
 * @property urlEncodedEntries Key-value entries used for [RequestBodyMode.X_WWW_FORM_URLENCODED] payloads.
 * @property graphQlState Structured GraphQL state model used when [mode] is [RequestBodyMode.GRAPHQL].
 */
data class RequestBodyState(
    val mode: RequestBodyMode = RequestBodyMode.JSON,
    val rawSubFormat: RawSubFormat = RawSubFormat.TEXT,
    val payloadText: String = "",
    val formDataEntries: List<KeyValueEntry> = emptyList(),
    val urlEncodedEntries: List<KeyValueEntry> = emptyList(),
    val graphQlState: GraphQlState = GraphQlState()
) {
    companion object {
        /**
         * Automatically detects and hydrates a strongly-typed [RequestBodyState] for HTTP Request authoring.
         *
         * - Auto-detects GraphQL JSON payloads or GraphQL endpoints, setting [RequestBodyMode.GRAPHQL] and populating [GraphQlState].
         * - Auto-detects `multipart/form-data` or `application/x-www-form-urlencoded` payloads into [formDataEntries].
         * - Auto-detects standard JSON payloads into [RequestBodyMode.JSON].
         * - Auto-detects XML, HTML, JavaScript, and Plain text into [RequestBodyMode.RAW] with the appropriate [RawSubFormat].
         *
         * @param headers HTTP headers list as key-value pairs.
         * @param rawBody Raw request body payload string.
         * @return Hydrated [RequestBodyState] configured for request authoring.
         */
        fun fromPayload(headers: List<Pair<String, String>>, rawBody: String): RequestBodyState {
            val trimmed = rawBody.trim()
            if (trimmed.isEmpty()) {
                return RequestBodyState(mode = RequestBodyMode.NONE, payloadText = "")
            }

            val headersMap = headers.toMap()
            return when (val resolvedFormat = BodyFormatterRegistry.resolveFormat(headersMap, trimmed)) {
                is BodyFormat.GraphQL -> {
                    val parsedGraphQlState = GraphQlPayloadMapper().parsePayload(trimmed)
                    RequestBodyState(
                        mode = RequestBodyMode.GRAPHQL,
                        payloadText = trimmed,
                        graphQlState = parsedGraphQlState
                    )
                }

                is BodyFormat.FormData -> {
                    val entries = resolvedFormat.pairs.mapIndexed { idx, (k, v) ->
                        KeyValueEntry(id = "form_$idx", key = k, value = v)
                    }
                    RequestBodyState(
                        mode = RequestBodyMode.FORM_DATA,
                        payloadText = trimmed,
                        formDataEntries = entries
                    )
                }

                is BodyFormat.Json -> {
                    RequestBodyState(
                        mode = RequestBodyMode.JSON,
                        payloadText = resolvedFormat.formattedText
                    )
                }

                is BodyFormat.Cbor -> {
                    RequestBodyState(
                        mode = RequestBodyMode.JSON,
                        payloadText = resolvedFormat.formattedText
                    )
                }

                is BodyFormat.Xml -> {
                    RequestBodyState(
                        mode = RequestBodyMode.RAW,
                        rawSubFormat = RawSubFormat.XML,
                        payloadText = resolvedFormat.formattedText
                    )
                }

                is BodyFormat.Html -> {
                    RequestBodyState(
                        mode = RequestBodyMode.RAW,
                        rawSubFormat = RawSubFormat.HTML,
                        payloadText = resolvedFormat.formattedText
                    )
                }

                is BodyFormat.Js -> {
                    RequestBodyState(
                        mode = RequestBodyMode.RAW,
                        rawSubFormat = RawSubFormat.JAVASCRIPT,
                        payloadText = resolvedFormat.formattedText
                    )
                }

                is BodyFormat.Css -> {
                    RequestBodyState(
                        mode = RequestBodyMode.RAW,
                        rawSubFormat = RawSubFormat.TEXT,
                        payloadText = resolvedFormat.formattedText
                    )
                }

                else -> {
                    RequestBodyState(
                        mode = RequestBodyMode.RAW,
                        rawSubFormat = RawSubFormat.TEXT,
                        payloadText = trimmed
                    )
                }
            }
        }
    }
}

/**
 * Immutable DTO holding the complete Body payload editor configuration for a single HTTP Response.
 *
 * Dedicated strictly to server responses: zero client form-data or GraphQL editor state pollution.
 *
 * @property mode Active body payload mode for responses (None, JSON, XML, HTML, Text, Raw).
 * @property payloadText Raw text/JSON/XML/HTML/JS payload content for response inspection & editing.
 */
data class ResponseBodyState(
    val mode: ResponseBodyMode = ResponseBodyMode.JSON,
    val payloadText: String = ""
) {
    companion object {
        /**
         * Automatically detects and hydrates a strongly-typed [ResponseBodyState] for HTTP Response inspection & editing.
         *
         * - Auto-detects standard JSON payloads into [ResponseBodyMode.JSON].
         * - Auto-detects XML into [ResponseBodyMode.XML].
         * - Auto-detects HTML into [ResponseBodyMode.HTML].
         * - Auto-detects Plain text into [ResponseBodyMode.TEXT].
         * - Auto-detects fallback formats into [ResponseBodyMode.RAW].
         *
         * @param headers HTTP headers list as key-value pairs.
         * @param rawBody Raw response body payload string.
         * @return Hydrated [ResponseBodyState] configured for response editing.
         */
        fun fromPayload(headers: List<Pair<String, String>>, rawBody: String): ResponseBodyState {
            val trimmed = rawBody.trim()
            if (trimmed.isEmpty()) {
                return ResponseBodyState(mode = ResponseBodyMode.NONE, payloadText = "")
            }

            val headersMap = headers.toMap()
            return when (val resolvedFormat = BodyFormatterRegistry.resolveFormat(headersMap, trimmed)) {
                is BodyFormat.Json -> {
                    ResponseBodyState(
                        mode = ResponseBodyMode.JSON,
                        payloadText = resolvedFormat.formattedText
                    )
                }

                is BodyFormat.Cbor -> {
                    ResponseBodyState(
                        mode = ResponseBodyMode.JSON,
                        payloadText = resolvedFormat.formattedText
                    )
                }

                is BodyFormat.GraphQL -> {
                    ResponseBodyState(
                        mode = ResponseBodyMode.JSON,
                        payloadText = trimmed
                    )
                }

                is BodyFormat.Xml -> {
                    ResponseBodyState(
                        mode = ResponseBodyMode.XML,
                        payloadText = resolvedFormat.formattedText
                    )
                }

                is BodyFormat.Html -> {
                    ResponseBodyState(
                        mode = ResponseBodyMode.HTML,
                        payloadText = resolvedFormat.formattedText
                    )
                }

                is BodyFormat.Js -> {
                    ResponseBodyState(
                        mode = ResponseBodyMode.RAW,
                        payloadText = resolvedFormat.formattedText
                    )
                }

                is BodyFormat.Css -> {
                    ResponseBodyState(
                        mode = ResponseBodyMode.RAW,
                        payloadText = resolvedFormat.formattedText
                    )
                }

                else -> {
                    val isTextPlain = headersMap.entries.find {
                        it.key.equals(
                            "content-type",
                            ignoreCase = true
                        )
                    }?.value?.contains("text/plain", ignoreCase = true) == true
                    ResponseBodyState(
                        mode = if (isTextPlain) ResponseBodyMode.TEXT else ResponseBodyMode.RAW,
                        payloadText = trimmed
                    )
                }
            }
        }
    }
}
