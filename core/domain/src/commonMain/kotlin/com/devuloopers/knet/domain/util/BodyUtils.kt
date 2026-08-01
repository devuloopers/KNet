package com.devuloopers.knet.domain.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Pure Kotlin Multiplatform utility functions for decoding and formatting HTTP request/response body payloads.
 */

private val BINARY_CONTENT_TYPES = setOf(
    "application/x-protobuf",
    "application/protobuf",
    "application/vnd.google.protobuf",
    "application/octet-stream",
    "application/grpc",
    "image/",
    "audio/",
    "video/",
    "font/"
)

/**
 * Returns true if the Content-Type indicates a non-text binary payload.
 */
fun isBinaryContentType(contentType: String?): Boolean {
    if (contentType == null) return false
    val lower = contentType.lowercase()
    return BINARY_CONTENT_TYPES.any { lower.contains(it) }
}

/**
 * Safely decodes a byte array into a UTF-8 string payload.
 */
fun decodeBodyToText(
    body: ByteArray?,
    headers: List<Pair<String, String>> = emptyList()
): String {
    if (body == null || body.isEmpty()) return ""
    val contentType = headers.firstOrNull { it.first.equals("Content-Type", ignoreCase = true) }?.second
    if (isBinaryContentType(contentType)) {
        return "[Binary Payload - ${body.size} bytes]"
    }
    return try {
        body.decodeToString()
    } catch (_: Throwable) {
        "[Binary Data - ${body.size} bytes]"
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
