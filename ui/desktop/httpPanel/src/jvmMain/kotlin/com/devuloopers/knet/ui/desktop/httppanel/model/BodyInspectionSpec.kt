package com.devuloopers.knet.ui.desktop.httppanel.model

import com.devuloopers.knet.engine.formatter.model.BodyFormat
import com.devuloopers.knet.engine.formatter.registry.BodyFormatterRegistry
import com.devuloopers.knet.ui.desktop.codeeditor.model.CodeLanguage

/**
 * Immutable strongly-typed specification for inspecting HTTP request or response body payloads.
 *
 * Encapsulates the raw payload, associated transport headers, optional pre-calculated [BodyFormat]
 * descriptor, and asynchronous preparation state to provide a single, cohesive contract across inspectors.
 *
 * @property headers Associated transport headers as key-value pairs.
 * @property rawBody Pristine wire string payload.
 * @property resolvedFormat Optional pre-resolved [BodyFormat] descriptor from off-thread preparation.
 * @property isPreparing True if the body payload is actively being loaded from storage or processed off-thread.
 */
public data class BodyInspectionSpec(
    val headers: List<Pair<String, String>> = emptyList(),
    val rawBody: String = "",
    val resolvedFormat: BodyFormat? = null,
    val isPreparing: Boolean = false
) {
    /**
     * True if the specification contains no body payload and is not currently in a loading state.
     */
    val isEmpty: Boolean
        get() = !isPreparing && rawBody.isBlank() && resolvedFormat == null

    /**
     * Resolves the strongly-typed [CodeLanguage] syntax highlighting token for this payload.
     */
    val codeLanguage: CodeLanguage
        get() = resolvedFormat?.let { CodeLanguage.fromBodyFormat(it) } ?: CodeLanguage.PLAIN

    /**
     * Formatted, pretty-printed representation of the payload (or raw string if formatting is unavailable).
     */
    val formattedText: String
        get() = when (val format = resolvedFormat) {
            is BodyFormat.Json -> format.formattedText
            is BodyFormat.Xml -> format.formattedText
            is BodyFormat.Html -> format.formattedText
            is BodyFormat.Js -> format.formattedText
            is BodyFormat.Css -> format.formattedText
            is BodyFormat.GraphQL -> format.queryText
            is BodyFormat.Cbor,
            is BodyFormat.Protobuf,
            is BodyFormat.GrpcWeb -> BodyFormatterRegistry.prettyPrintBody(headers.toMap(), rawBody)
            else -> rawBody
        }

    public companion object {
        /**
         * Creates a [BodyInspectionSpec] by resolving the strongly-typed [BodyFormat]
         * from HTTP headers and wire body text using the central [BodyFormatterRegistry].
         *
         * @param headers Associated transport headers as key-value pairs.
         * @param rawBody Pristine wire string payload.
         * @param isPreparing True if the payload is actively loading.
         * @return Fully resolved [BodyInspectionSpec].
         */
        public fun fromPayload(
            headers: List<Pair<String, String>>,
            rawBody: String,
            isPreparing: Boolean = false
        ): BodyInspectionSpec {
            val trimmed = rawBody.trim()
            if (trimmed.isEmpty()) {
                return BodyInspectionSpec(
                    headers = headers,
                    rawBody = rawBody,
                    resolvedFormat = null,
                    isPreparing = isPreparing
                )
            }

            val headersMap = headers.toMap()
            val format = BodyFormatterRegistry.resolveFormat(headersMap, trimmed)
            return BodyInspectionSpec(
                headers = headers,
                rawBody = rawBody,
                resolvedFormat = format,
                isPreparing = isPreparing
            )
        }

        /**
         * Overload creating a [BodyInspectionSpec] from a headers map and wire body text.
         *
         * @param headers Associated transport headers map.
         * @param rawBody Pristine wire string payload.
         * @param isPreparing True if the payload is actively loading.
         * @return Fully resolved [BodyInspectionSpec].
         */
        public fun fromPayload(
            headers: Map<String, String>,
            rawBody: String,
            isPreparing: Boolean = false
        ): BodyInspectionSpec {
            return fromPayload(
                headers = headers.entries.map { it.key to it.value },
                rawBody = rawBody,
                isPreparing = isPreparing
            )
        }
    }
}
