package com.devuloopers.knet.ui.desktop.httppanel.model

import com.devuloopers.knet.ui.core.components.keyvalue.KeyValueEntry

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
 * Immutable DTO holding the complete Body payload editor configuration for a single request.
 *
 * @property mode Active body payload mode (None, JSON, form-data, url-encoded, raw, GraphQL).
 * @property rawSubFormat Active raw sub-format selector when [mode] is [BodyMode.RAW].
 * @property payloadText Raw text/JSON/XML/HTML/JS/GraphQL payload content for text-based modes.
 * @property formDataEntries Key-value entries used for [BodyMode.FORM_DATA] multipart payloads.
 * @property urlEncodedEntries Key-value entries used for [BodyMode.X_WWW_FORM_URLENCODED] payloads.
 */
public data class BodyState(
    val mode: BodyMode = BodyMode.JSON,
    val rawSubFormat: RawSubFormat = RawSubFormat.TEXT,
    val payloadText: String = "",
    val formDataEntries: List<KeyValueEntry> = emptyList(),
    val urlEncodedEntries: List<KeyValueEntry> = emptyList()
)
