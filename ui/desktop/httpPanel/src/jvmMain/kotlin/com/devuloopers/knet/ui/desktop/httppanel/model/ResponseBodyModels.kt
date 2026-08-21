package com.devuloopers.knet.ui.desktop.httppanel.model

import com.devuloopers.knet.engine.formatter.formatters.HtmlBodyFormatter
import com.devuloopers.knet.engine.formatter.formatters.JsonBodyFormatter
import com.devuloopers.knet.engine.formatter.formatters.XmlBodyFormatter
import com.devuloopers.knet.engine.formatter.model.BodyFormat
import com.devuloopers.knet.ui.desktop.codeeditor.model.CodeLanguage

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
    NONE("none", CodeLanguage.PLAIN, "", isPrettifiable = false), JSON(
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
        ResponseBodyTemplateResources.html,
        isPrettifiable = true
    ),
    TEXT("text", CodeLanguage.PLAIN, "// Enter plain text response body...", isPrettifiable = false), RAW(
        "raw",
        CodeLanguage.PLAIN,
        "// Enter raw response payload...",
        isPrettifiable = false
    );

    /**
     * Formats the payload text according to this response mode's syntax rules.
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
 * Immutable DTO holding the complete Body payload editor configuration for a single HTTP Response.
 *
 * Dedicated strictly to server responses: zero client form-data or GraphQL editor state pollution.
 *
 * @property mode Active body payload mode for responses (None, JSON, XML, HTML, Text, Raw).
 * @property payloadText Raw text/JSON/XML/HTML/JS payload content for response inspection & editing.
 */
data class ResponseBodyState(
    val mode: ResponseBodyMode = ResponseBodyMode.JSON, val payloadText: String = ""
) {
    companion object {
        /**
         * Constructs a [ResponseBodyState] directly from a [PayloadInspectionSpec].
         *
         * Maps the pre-resolved [BodyFormat] to the appropriate [ResponseBodyMode].
         *
         * @param spec The resolved [PayloadInspectionSpec].
         * @return Hydrated [ResponseBodyState] configured for response editing.
         */
        fun from(spec: PayloadInspectionSpec): ResponseBodyState {
            val trimmed = spec.rawBody.trim()
            if (trimmed.isEmpty()) {
                return ResponseBodyState(mode = ResponseBodyMode.NONE, payloadText = "")
            }
            val headersMap = spec.headers.toMap()
            val isTextPlain = headersMap.entries.find {
                it.key.equals("content-type", ignoreCase = true)
            }?.value?.contains("text/plain", ignoreCase = true) == true

            return when (val format = spec.resolvedFormat) {
                is BodyFormat.Json -> ResponseBodyState(
                    mode = ResponseBodyMode.JSON, payloadText = format.textContent.ifEmpty { trimmed }
                )

                is BodyFormat.Cbor -> ResponseBodyState(
                    mode = ResponseBodyMode.JSON, payloadText = format.textContent.ifEmpty { trimmed }
                )

                is BodyFormat.GraphQL -> ResponseBodyState(mode = ResponseBodyMode.JSON, payloadText = trimmed)
                is BodyFormat.Xml -> ResponseBodyState(
                    mode = ResponseBodyMode.XML, payloadText = format.textContent.ifEmpty { trimmed }
                )

                is BodyFormat.Html -> ResponseBodyState(
                    mode = ResponseBodyMode.HTML, payloadText = format.textContent.ifEmpty { trimmed }
                )

                is BodyFormat.Js -> ResponseBodyState(
                    mode = ResponseBodyMode.RAW, payloadText = format.textContent.ifEmpty { trimmed }
                )

                is BodyFormat.Css -> ResponseBodyState(
                    mode = ResponseBodyMode.RAW, payloadText = format.textContent.ifEmpty { trimmed }
                )

                null -> ResponseBodyState(mode = ResponseBodyMode.NONE, payloadText = "")
                else -> ResponseBodyState(
                    mode = if (isTextPlain) ResponseBodyMode.TEXT else ResponseBodyMode.RAW, payloadText = trimmed
                )
            }
        }
    }
}
