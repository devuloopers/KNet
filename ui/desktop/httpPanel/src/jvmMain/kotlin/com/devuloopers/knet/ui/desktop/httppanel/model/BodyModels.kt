package com.devuloopers.knet.ui.desktop.httppanel.model

import com.devuloopers.knet.engine.formatter.model.BodyFormat
import com.devuloopers.knet.engine.formatter.registry.BodyFormatterRegistry
import com.devuloopers.knet.ui.core.components.keyvalue.KeyValueEntry
import com.devuloopers.knet.ui.desktop.httppanel.mapper.GraphQlPayloadMapper

/**
 * Strongly-typed body payload mode for HTTP request authoring in API Studio.
 *
 * @property label User-facing display label for the mode pill selector.
 * @property tabLabel Short dynamic label used in the Body sub-tab header (e.g. "Body (JSON)").
 */
public enum class BodyMode(val label: String, val tabLabel: String) {
    NONE("none", "Body"),
    JSON("json", "Body (JSON)"),
    FORM_DATA("form-data", "Body (form-data)"),
    X_WWW_FORM_URLENCODED("x-www-form-urlencoded", "Body (url-encoded)"),
    RAW("raw", "Body (raw)"),
    GRAPHQL("graphql", "Body (GraphQL)")
}

/**
 * Strongly-typed raw body sub-format selector for the [BodyMode.RAW] payload mode.
 *
 * Each variant controls the syntax highlighting language hint passed to [KNetCodeEditor]
 * and the MIME Content-Type that should be set on the request.
 *
 * @property label User-facing display label for the format dropdown.
 * @property languageHint Language token forwarded to [KNetCodeEditor] for syntax highlighting.
 * @property contentType HTTP Content-Type MIME type produced by this raw sub-format.
 */
public enum class RawSubFormat(val label: String, val languageHint: String, val contentType: String) {
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
public enum class GraphQlSubTab(val label: String) {
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
public data class GraphQlState(
    val queryText: String = "",
    val variablesText: String = DEFAULT_JSON_OBJECT_PLACEHOLDER,
    val operationName: String = "",
    val extensionsText: String = DEFAULT_JSON_OBJECT_PLACEHOLDER,
    val activeSubTab: GraphQlSubTab = GraphQlSubTab.QUERY
) {
    public companion object {
        public const val DEFAULT_JSON_OBJECT_PLACEHOLDER: String = "{\n  \n}"
    }
}

/**
 * Immutable DTO holding the complete Body payload editor configuration for a single request.
 *
 * @property mode Active body payload mode (None, JSON, form-data, url-encoded, raw, GraphQL).
 * @property rawSubFormat Active raw sub-format selector when [mode] is [BodyMode.RAW].
 * @property payloadText Raw text/JSON/XML/HTML/JS/GraphQL payload content for text-based modes.
 * @property formDataEntries Key-value entries used for [BodyMode.FORM_DATA] multipart payloads.
 * @property urlEncodedEntries Key-value entries used for [BodyMode.X_WWW_FORM_URLENCODED] payloads.
 * @property graphQlState Structured GraphQL state model used when [mode] is [BodyMode.GRAPHQL].
 */
public data class BodyState(
    val mode: BodyMode = BodyMode.JSON,
    val rawSubFormat: RawSubFormat = RawSubFormat.TEXT,
    val payloadText: String = "",
    val formDataEntries: List<KeyValueEntry> = emptyList(),
    val urlEncodedEntries: List<KeyValueEntry> = emptyList(),
    val graphQlState: GraphQlState = GraphQlState()
) {
    public companion object {
        /**
         * Automatically detects and hydrates a strongly-typed [BodyState] from raw HTTP wire headers and payload string.
         *
         * - Auto-detects GraphQL JSON payloads or GraphQL endpoints, setting [BodyMode.GRAPHQL] and populating [GraphQlState].
         * - Auto-detects `multipart/form-data` or `application/x-www-form-urlencoded` payloads into [formDataEntries].
         * - Auto-detects standard JSON payloads into [BodyMode.JSON].
         * - Auto-detects XML, HTML, JavaScript, and Plain text into [BodyMode.RAW] with the appropriate [RawSubFormat].
         *
         * @param headers HTTP headers list as key-value pairs.
         * @param rawBody Raw request body payload string.
         * @return Hydrated [BodyState] with resolved [BodyMode] and sub-models.
         */
        public fun fromPayload(headers: List<Pair<String, String>>, rawBody: String): BodyState {
            val trimmed = rawBody.trim()
            if (trimmed.isEmpty()) {
                return BodyState(mode = BodyMode.NONE, payloadText = "")
            }

            val headersMap = headers.toMap()
            return when (val resolvedFormat = BodyFormatterRegistry.resolveFormat(headersMap, trimmed)) {
                is BodyFormat.GraphQL -> {
                    val parsedGraphQlState = GraphQlPayloadMapper().parsePayload(trimmed)
                    BodyState(
                        mode = BodyMode.GRAPHQL,
                        payloadText = trimmed,
                        graphQlState = parsedGraphQlState
                    )
                }

                is BodyFormat.FormData -> {
                    val entries = resolvedFormat.pairs.mapIndexed { idx, (k, v) ->
                        KeyValueEntry(id = "form_$idx", key = k, value = v)
                    }
                    BodyState(
                        mode = BodyMode.FORM_DATA,
                        payloadText = trimmed,
                        formDataEntries = entries
                    )
                }

                is BodyFormat.Json -> {
                    BodyState(
                        mode = BodyMode.JSON,
                        payloadText = trimmed
                    )
                }

                is BodyFormat.Xml -> {
                    BodyState(
                        mode = BodyMode.RAW,
                        rawSubFormat = RawSubFormat.XML,
                        payloadText = trimmed
                    )
                }

                is BodyFormat.Html -> {
                    BodyState(
                        mode = BodyMode.RAW,
                        rawSubFormat = RawSubFormat.HTML,
                        payloadText = trimmed
                    )
                }

                is BodyFormat.Js -> {
                    BodyState(
                        mode = BodyMode.RAW,
                        rawSubFormat = RawSubFormat.JAVASCRIPT,
                        payloadText = trimmed
                    )
                }

                else -> {
                    BodyState(
                        mode = BodyMode.RAW,
                        rawSubFormat = RawSubFormat.TEXT,
                        payloadText = trimmed
                    )
                }
            }
        }
    }
}
