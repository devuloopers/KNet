package com.devuloopers.knet.ui.desktop.httppanel.model

import com.devuloopers.knet.engine.formatter.formatters.HtmlBodyFormatter
import com.devuloopers.knet.engine.formatter.formatters.JsonBodyFormatter
import com.devuloopers.knet.engine.formatter.formatters.XmlBodyFormatter
import com.devuloopers.knet.engine.formatter.model.BodyFormat
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
        JSON -> JsonBodyFormatter.prettyPrintJson(payload)
        XML -> XmlBodyFormatter.prettyPrint(payload)
        HTML -> HtmlBodyFormatter.prettyPrintHtml(payload)
        else -> payload
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
         * Constructs a [RequestBodyState] directly from a [PayloadInspectionSpec].
         *
         * Maps the pre-resolved [BodyFormat] to the appropriate [RequestBodyMode],
         * hydrating [GraphQlState] or [formDataEntries] when structured payloads are present.
         *
         * @param spec The resolved [PayloadInspectionSpec].
         * @return Hydrated [RequestBodyState] configured for request authoring.
         */
        fun from(spec: PayloadInspectionSpec): RequestBodyState {
            val trimmed = spec.rawBody.trim()
            if (trimmed.isEmpty()) {
                return RequestBodyState(mode = RequestBodyMode.NONE, payloadText = "")
            }
            return when (val format = spec.resolvedFormat) {
                is BodyFormat.GraphQL -> {
                    val parsedGraphQlState = GraphQlPayloadMapper().parseToUi(trimmed)
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
                    payloadText = format.textContent.ifEmpty { trimmed }
                )
                is BodyFormat.Cbor -> RequestBodyState(
                    mode = RequestBodyMode.JSON,
                    payloadText = format.textContent.ifEmpty { trimmed }
                )
                is BodyFormat.Xml -> RequestBodyState(
                    mode = RequestBodyMode.RAW,
                    rawSubFormat = RawSubFormat.XML,
                    payloadText = format.textContent.ifEmpty { trimmed }
                )
                is BodyFormat.Html -> RequestBodyState(
                    mode = RequestBodyMode.RAW,
                    rawSubFormat = RawSubFormat.HTML,
                    payloadText = format.textContent.ifEmpty { trimmed }
                )
                is BodyFormat.Js -> RequestBodyState(
                    mode = RequestBodyMode.RAW,
                    rawSubFormat = RawSubFormat.JAVASCRIPT,
                    payloadText = format.textContent.ifEmpty { trimmed }
                )
                is BodyFormat.Css -> RequestBodyState(
                    mode = RequestBodyMode.RAW,
                    rawSubFormat = RawSubFormat.TEXT,
                    payloadText = format.textContent.ifEmpty { trimmed }
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
