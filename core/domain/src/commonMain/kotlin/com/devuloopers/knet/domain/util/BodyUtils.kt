package com.devuloopers.knet.domain.util

import com.devuloopers.knet.domain.clientNetwork.decoder.BodyDecoder
import com.devuloopers.knet.domain.clientNetwork.decoder.BodyTextDecoder
import com.devuloopers.knet.domain.clientNetwork.decoder.DecodedTextResult
import com.devuloopers.knet.domain.clientNetwork.decoder.MediaTypeInspector
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Pure Kotlin Multiplatform utility functions for decoding and formatting HTTP request/response body payloads.
 */

/**
 * Returns true if the Content-Type indicates a non-text binary payload category.
 */
fun isBinaryContentType(contentType: String?): Boolean {
    return MediaTypeInspector.inspectCategory(contentType) != null
}

/**
 * Safely decodes a byte array into a UTF-8 string payload, executing transport Content-Encoding decompression.
 */
fun decodeBodyToText(
    body: ByteArray?,
    headers: List<Pair<String, String>> = emptyList()
): String {
    val decodedBodyResult = BodyDecoder.decode(body, headers)
    return when (val textResult = BodyTextDecoder.decode(decodedBodyResult, headers)) {
        is DecodedTextResult.Success -> textResult.text
        is DecodedTextResult.BinaryKnownType -> "[Binary Payload - ${textResult.size} B (${textResult.category.name})]"
        is DecodedTextResult.BinaryUnknownType -> "[Binary Payload - ${textResult.size} B]"
        is DecodedTextResult.UnsupportedEncoding -> "[Unsupported Content-Encoding: '${textResult.encoding}' (${textResult.size} B)]"
        is DecodedTextResult.DecodingError -> "[Decompression Failed (${textResult.encoding}): ${textResult.errorMessage}]"
    }
}

/**
 * Pretty prints JSON strings if valid, otherwise returns original text.
 */
fun formatJsonIfPossible(rawJson: String): String {
    if (rawJson.isBlank()) return rawJson
    return try {
        val json = Json { prettyPrint = true }
        val element = json.parseToJsonElement(rawJson)
        json.encodeToString(JsonElement.serializer(), element)
    } catch (_: Throwable) {
        rawJson
    }
}
