package com.devuloopers.knet.domain.clientNetwork.usecase

import com.devuloopers.knet.domain.clientNetwork.model.MimeType
import com.devuloopers.knet.domain.util.MimeTypeUtils
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Domain UseCase that formats raw response payload strings into formatted JSON/XML for UI viewing.
 */
class FormatResponseBodyUseCase {

    private val prettyJson = Json { prettyPrint = true }

    /**
     * Formats a raw payload string into pretty-printed JSON/XML based on strongly-typed [mimeType] or payload structure.
     *
     * @param rawBody Raw response body payload string.
     * @param mimeType Response strongly-typed [MimeType] enum instance.
     * @return Formatted payload string.
     */
    fun execute(rawBody: String, mimeType: MimeType = MimeType.UNKNOWN): String {
        if (rawBody.isBlank()) return ""

        val trimmed = rawBody.trim()
        if (MimeTypeUtils.isJson(mimeType, trimmed)) {
            return try {
                val element = Json.parseToJsonElement(trimmed)
                prettyJson.encodeToString(JsonElement.serializer(), element)
            } catch (_: Exception) {
                rawBody
            }
        }

        return rawBody
    }

    /**
     * Overload accepting raw MIME type string for convenience.
     */
    fun execute(rawBody: String, mimeType: String): String {
        return execute(rawBody, MimeType.fromString(mimeType))
    }
}
