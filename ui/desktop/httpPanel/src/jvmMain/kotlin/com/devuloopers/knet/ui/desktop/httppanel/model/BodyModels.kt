package com.devuloopers.knet.ui.desktop.httppanel.model

import com.devuloopers.knet.engine.formatter.formatters.GraphQLBodyFormatter
import com.devuloopers.knet.engine.formatter.formatters.HtmlBodyFormatter
import com.devuloopers.knet.engine.formatter.formatters.JsonBodyFormatter
import com.devuloopers.knet.engine.formatter.formatters.XmlBodyFormatter
import com.devuloopers.knet.engine.formatter.graphql.GraphQLQuerySynchronizer
import com.devuloopers.knet.engine.formatter.model.BodyFormat
import com.devuloopers.knet.engine.formatter.registry.BodyFormatterRegistry
import com.devuloopers.knet.ui.core.components.keyvalue.KeyValueEntry
import com.devuloopers.knet.ui.desktop.codeeditor.model.CodeLanguage
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
 * @property codeLanguage Strongly-typed [CodeLanguage] passed to [KNetCodeEditor] for syntax highlighting.
 * @property placeholder Default template or placeholder string shown when payload is empty.
 * @property isPrettifiable True if this response format supports automatic syntax prettification.
 */
enum class ResponseBodyMode(
    val label: String,
    val codeLanguage: CodeLanguage = CodeLanguage.PLAIN,
    val placeholder: String = "",
    val isPrettifiable: Boolean = false
) {
    NONE("none", CodeLanguage.PLAIN, "", isPrettifiable = false),
    JSON(
        "json",
        CodeLanguage.JSON,
        "// Enter JSON response body...\n{\n  \"status\": \"success\"\n}",
        isPrettifiable = true
    ),
    XML(
        "xml",
        CodeLanguage.XML,
        "<!-- Enter XML response body -->\n<response>\n  <status>success</status>\n</response>",
        isPrettifiable = true
    ),
    HTML(
        "html",
        CodeLanguage.HTML,
        "<!-- Enter HTML response body -->\n<!DOCTYPE html>\n<html>\n<body>\n  <h1>200 OK</h1>\n</body>\n</html>",
        isPrettifiable = true
    ),
    TEXT("text", CodeLanguage.PLAIN, "// Enter plain text response body...", isPrettifiable = false),
    RAW("raw", CodeLanguage.PLAIN, "// Enter raw response payload...", isPrettifiable = false);

    /**
     * Formats the payload text according to this response mode's syntax rules.
     *
     * @param payload Raw payload string to format.
     * @return Pretty-printed string if formatting succeeds, or original payload text on failure.
     */
    fun prettify(payload: String): String = when (this) {
        JSON -> JsonBodyFormatter().prettyPrintJson(payload)
        XML -> XmlBodyFormatter().prettyPrint(payload)
        HTML -> HtmlBodyFormatter.prettyPrintHtml(payload)
        else -> payload
    }
}

/**
 * Strongly-typed raw body sub-format selector for the [RequestBodyMode.RAW] payload mode.
 *
 * Each variant controls the syntax highlighting language hint passed to [KNetCodeEditor]
 * and the MIME Content-Type that should be set on the request.
 *
 * @property label User-facing display label for the format dropdown.
 * @property codeLanguage Strongly-typed [CodeLanguage] passed to [KNetCodeEditor] for syntax highlighting.
 * @property contentType HTTP Content-Type MIME type produced by this raw sub-format.
 * @property isPrettifiable True if this raw sub-format supports automatic syntax prettification.
 */
enum class RawSubFormat(
    val label: String,
    val codeLanguage: CodeLanguage,
    val contentType: String,
    val isPrettifiable: Boolean = false
) {
    TEXT("Text", CodeLanguage.PLAIN, "text/plain", isPrettifiable = false),
    JSON("JSON", CodeLanguage.JSON, "application/json", isPrettifiable = true),
    XML("XML", CodeLanguage.XML, "application/xml", isPrettifiable = true),
    HTML("HTML", CodeLanguage.HTML, "text/html", isPrettifiable = true),
    JAVASCRIPT("JavaScript", CodeLanguage.JAVASCRIPT, "text/javascript", isPrettifiable = false);

    /**
     * Formats the payload text according to this raw sub-format syntax rules.
     *
     * @param payload Raw payload string to format.
     * @return Pretty-printed string if formatting succeeds, or original payload text on failure.
     */
    fun prettify(payload: String): String = when (this) {
        JSON -> JsonBodyFormatter().prettyPrintJson(payload)
        XML -> XmlBodyFormatter().prettyPrint(payload)
        HTML -> HtmlBodyFormatter.prettyPrintHtml(payload)
        else -> payload
    }
}

/**
 * Strongly-typed sub-tabs for structured GraphQL request authoring.
 *
 * Serves as the Single Source of Truth (SSOT) for sub-tab metadata, syntax highlighting,
 * empty templates, payload accessors, and prettification logic.
 *
 * @property label User-facing display label for the sub-tab chip.
 * @property codeLanguage Strongly-typed [CodeLanguage] passed to [KNetCodeEditor] for syntax highlighting.
 * @property placeholder Default template or placeholder string shown when the editor buffer is empty.
 */
enum class GraphQlSubTab(
    val label: String,
    val codeLanguage: CodeLanguage,
    val placeholder: String
) {
    QUERY(
        label = "Query",
        codeLanguage = CodeLanguage.GRAPHQL,
        placeholder = "# Enter GraphQL Query or Mutation...\nquery GetUser {\n  user(id: 1) {\n    name\n    email\n  }\n}"
    ),
    VARIABLES(
        label = "Variables (JSON)",
        codeLanguage = CodeLanguage.JSON,
        placeholder = "// Enter GraphQL variables as JSON...\n{\n  \"id\": \"123\"\n}"
    ),
    EXTENSIONS(
        label = "Extensions (JSON)",
        codeLanguage = CodeLanguage.JSON,
        placeholder = "// Enter GraphQL extensions metadata as JSON...\n{\n  \"clientLibrary\": {\n    \"name\": \"apollo-kotlin\",\n    \"version\": \"5.0.0\"\n  }\n}"
    );

    /**
     * Extracts the active payload text for this sub-tab from the given [GraphQlState].
     *
     * @param state Immutable [GraphQlState] holding the GraphQL parameters.
     * @return String content for the active sub-tab.
     */
    fun getPayload(state: GraphQlState): String = when (this) {
        QUERY -> state.queryText
        VARIABLES -> state.variablesText
        EXTENSIONS -> state.extensionsText
    }

    /**
     * Updates the appropriate payload field in [GraphQlState] with new user-authored text.
     * For [QUERY], automatically extracts and synchronizes the active operation name.
     *
     * @param state Current [GraphQlState].
     * @param newText New payload text entered by the user.
     * @return Updated [GraphQlState].
     */
    fun updatePayload(state: GraphQlState, newText: String): GraphQlState = when (this) {
        QUERY -> {
            val extractedOpName = GraphQLQuerySynchronizer.extractOperationName(newText) ?: ""
            state.copy(queryText = newText, operationName = extractedOpName)
        }
        VARIABLES -> state.copy(variablesText = newText)
        EXTENSIONS -> state.copy(extensionsText = newText)
    }

    /**
     * Prettifies the active sub-tab payload and returns an updated [GraphQlState].
     *
     * @param state Current [GraphQlState].
     * @return Updated [GraphQlState] with formatted payload.
     */
    fun prettify(state: GraphQlState): GraphQlState = when (this) {
        QUERY -> {
            val formatted = GraphQLBodyFormatter().formatQuery(state.queryText)
            val extractedOpName = GraphQLQuerySynchronizer.extractOperationName(formatted) ?: ""
            state.copy(queryText = formatted, operationName = extractedOpName)
        }
        VARIABLES -> {
            val trimmed = state.variablesText.trim()
            val formatted = if (trimmed.isEmpty() || trimmed == "{}") {
                GraphQlState.DEFAULT_JSON_OBJECT_PLACEHOLDER
            } else {
                JsonBodyFormatter().prettyPrintJson(state.variablesText)
            }
            state.copy(variablesText = formatted)
        }
        EXTENSIONS -> {
            val trimmed = state.extensionsText.trim()
            val formatted = if (trimmed.isEmpty() || trimmed == "{}") {
                GraphQlState.DEFAULT_JSON_OBJECT_PLACEHOLDER
            } else {
                JsonBodyFormatter().prettyPrintJson(state.extensionsText)
            }
            state.copy(extensionsText = formatted)
        }
    }
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

        /**
         * Constructs a [RequestBodyState] directly from a pre-resolved [PayloadInspectionSpec],
         * bypassing [BodyFormatterRegistry] entirely. The [BodyFormat] has already been
         * computed off-thread — this method only maps it to the appropriate [RequestBodyMode].
         *
         * @param spec Pre-resolved [PayloadInspectionSpec].
         * @return Hydrated [RequestBodyState] configured for request authoring.
         */
        fun fromResolved(spec: PayloadInspectionSpec): RequestBodyState {
            val trimmed = spec.rawBody.trim()
            if (trimmed.isEmpty()) {
                return RequestBodyState(mode = RequestBodyMode.NONE, payloadText = "")
            }
            return when (val format = spec.resolvedFormat) {
                is BodyFormat.GraphQL -> {
                    val parsedGraphQlState = GraphQlPayloadMapper().parsePayload(trimmed)
                    RequestBodyState(
                        mode = RequestBodyMode.GRAPHQL,
                        payloadText = trimmed,
                        graphQlState = parsedGraphQlState
                    )
                }
                is BodyFormat.FormData -> {
                    val entries = format.pairs.mapIndexed { idx, (k, v) ->
                        KeyValueEntry(id = "form_$idx", key = k, value = v)
                    }
                    RequestBodyState(
                        mode = RequestBodyMode.FORM_DATA,
                        payloadText = trimmed,
                        formDataEntries = entries
                    )
                }
                is BodyFormat.Json -> RequestBodyState(
                    mode = RequestBodyMode.JSON,
                    payloadText = format.formattedText
                )
                is BodyFormat.Cbor -> RequestBodyState(
                    mode = RequestBodyMode.JSON,
                    payloadText = format.formattedText
                )
                is BodyFormat.Xml -> RequestBodyState(
                    mode = RequestBodyMode.RAW,
                    rawSubFormat = RawSubFormat.XML,
                    payloadText = format.formattedText
                )
                is BodyFormat.Html -> RequestBodyState(
                    mode = RequestBodyMode.RAW,
                    rawSubFormat = RawSubFormat.HTML,
                    payloadText = format.formattedText
                )
                is BodyFormat.Js -> RequestBodyState(
                    mode = RequestBodyMode.RAW,
                    rawSubFormat = RawSubFormat.JAVASCRIPT,
                    payloadText = format.formattedText
                )
                is BodyFormat.Css -> RequestBodyState(
                    mode = RequestBodyMode.RAW,
                    rawSubFormat = RawSubFormat.TEXT,
                    payloadText = format.formattedText
                )
                null -> RequestBodyState(mode = RequestBodyMode.NONE, payloadText = "")
                else -> RequestBodyState(
                    mode = RequestBodyMode.RAW,
                    rawSubFormat = RawSubFormat.TEXT,
                    payloadText = trimmed
                )
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

        /**
         * Constructs a [ResponseBodyState] directly from a pre-resolved [PayloadInspectionSpec],
         * bypassing [BodyFormatterRegistry] entirely. The [BodyFormat] has already been
         * computed off-thread — this method only maps it to the appropriate [ResponseBodyMode].
         *
         * @param spec Pre-resolved [PayloadInspectionSpec].
         * @return Hydrated [ResponseBodyState] configured for response editing.
         */
        fun fromResolved(spec: PayloadInspectionSpec): ResponseBodyState {
            val trimmed = spec.rawBody.trim()
            if (trimmed.isEmpty()) {
                return ResponseBodyState(mode = ResponseBodyMode.NONE, payloadText = "")
            }
            val headersMap = spec.headers.toMap()
            val isTextPlain = headersMap.entries.find {
                it.key.equals("content-type", ignoreCase = true)
            }?.value?.contains("text/plain", ignoreCase = true) == true

            return when (val format = spec.resolvedFormat) {
                is BodyFormat.Json -> ResponseBodyState(mode = ResponseBodyMode.JSON, payloadText = format.formattedText)
                is BodyFormat.Cbor -> ResponseBodyState(mode = ResponseBodyMode.JSON, payloadText = format.formattedText)
                is BodyFormat.GraphQL -> ResponseBodyState(mode = ResponseBodyMode.JSON, payloadText = trimmed)
                is BodyFormat.Xml -> ResponseBodyState(mode = ResponseBodyMode.XML, payloadText = format.formattedText)
                is BodyFormat.Html -> ResponseBodyState(mode = ResponseBodyMode.HTML, payloadText = format.formattedText)
                is BodyFormat.Js -> ResponseBodyState(mode = ResponseBodyMode.RAW, payloadText = format.formattedText)
                is BodyFormat.Css -> ResponseBodyState(mode = ResponseBodyMode.RAW, payloadText = format.formattedText)
                null -> ResponseBodyState(mode = ResponseBodyMode.NONE, payloadText = "")
                else -> ResponseBodyState(
                    mode = if (isTextPlain) ResponseBodyMode.TEXT else ResponseBodyMode.RAW,
                    payloadText = trimmed
                )
            }
        }
    }
}
