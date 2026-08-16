package com.devuloopers.knet.ui.desktop.httppanel.model

import com.devuloopers.knet.domain.util.decodeBodyToText
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
data class PayloadInspectionSpec(
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
        get() = resolvedFormat.toCodeLanguage()

    /**
     * Formatted, pretty-printed representation of the payload (or raw string if formatting is unavailable).
     */
    val formattedText: String
        get() = (resolvedFormat as? BodyFormat.HasTextContent)?.textContent?.ifEmpty { rawBody } ?: rawBody

    companion object {
        /**
         * Sentinel empty instance representing an absent body payload.
         */
        val EMPTY: PayloadInspectionSpec = PayloadInspectionSpec()

        /**
         * Creates a fully-resolved [PayloadInspectionSpec] from raw wire body bytes and headers in a single pass.
         * Decodes Content-Encoding (gzip/deflate/br) decompression and resolves the [BodyFormat].
         *
         * @param body Raw compressed or plain byte array.
         * @param headers Associated transport headers.
         * @param isPreparing True if the payload is actively loading.
         * @return Fully resolved [PayloadInspectionSpec].
         */
        fun fromBytes(
            body: ByteArray?,
            headers: List<Pair<String, String>> = emptyList(),
            isPreparing: Boolean = false
        ): PayloadInspectionSpec {
            val decodedText = decodeBodyToText(body, headers)
            return fromPayload(
                headers = headers,
                rawBody = decodedText,
                isPreparing = isPreparing
            )
        }

        /**
         * Creates a [PayloadInspectionSpec] by resolving the strongly-typed [BodyFormat]
         * from HTTP headers and wire body text using the central [BodyFormatterRegistry].
         *
         * @param headers Associated transport headers as key-value pairs.
         * @param rawBody Pristine wire string payload.
         * @param isPreparing True if the payload is actively loading.
         * @return Fully resolved [PayloadInspectionSpec].
         */
        fun fromPayload(
            headers: List<Pair<String, String>>,
            rawBody: String,
            isPreparing: Boolean = false
        ): PayloadInspectionSpec {
            val trimmed = rawBody.trim()
            if (trimmed.isEmpty()) {
                return PayloadInspectionSpec(
                    headers = headers,
                    rawBody = rawBody,
                    resolvedFormat = null,
                    isPreparing = isPreparing
                )
            }

            val headersMap = headers.toMap()
            val format = BodyFormatterRegistry.resolveFormat(headersMap, trimmed)
            return PayloadInspectionSpec(
                headers = headers,
                rawBody = rawBody,
                resolvedFormat = format,
                isPreparing = isPreparing
            )
        }

        /**
         * Overload creating a [PayloadInspectionSpec] from a headers map and wire body text.
         *
         * @param headers Associated transport headers map.
         * @param rawBody Pristine wire string payload.
         * @param isPreparing True if the payload is actively loading.
         * @return Fully resolved [PayloadInspectionSpec].
         */
        fun fromPayload(
            headers: Map<String, String>,
            rawBody: String,
            isPreparing: Boolean = false
        ): PayloadInspectionSpec {
            return fromPayload(
                headers = headers.entries.map { it.key to it.value },
                rawBody = rawBody,
                isPreparing = isPreparing
            )
        }
    }
}
