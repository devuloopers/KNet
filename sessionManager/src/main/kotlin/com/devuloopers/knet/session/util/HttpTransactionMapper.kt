package com.devuloopers.knet.session.util

import com.devuloopers.knet.model.HttpRequest
import com.devuloopers.knet.model.HttpResponse
import com.devuloopers.knet.model.HttpTransaction
import com.devuloopers.knet.storage.HttpTransactionEntity

/**
 * Mapper utility responsible for translating Room database entities to domain DTOs.
 * Includes JSON header serialization helpers.
 */
object HttpTransactionMapper {

    /**
     * Serializes a list of HTTP headers into a lightweight JSON array structure.
     *
     * @param headers List of key-value header pairs.
     * @return Serialized JSON string.
     */
    fun serializeHeaders(headers: List<Pair<String, String>>): String {
        return headers.joinToString(separator = ",", prefix = "[", postfix = "]") { (k, v) ->
            "[\"${escapeJson(k)}\",\"${escapeJson(v)}\"]"
        }
    }

    /**
     * Deserializes a list of HTTP headers from a JSON array string.
     *
     * @param json Serialized JSON header string.
     * @return List of key-value header pairs.
     */
    fun deserializeHeaders(json: String): List<Pair<String, String>> {
        if (json == "[]" || json.isEmpty()) return emptyList()
        val result = mutableListOf<Pair<String, String>>()
        val matches = Regex("\\[\"(.*?)\",\"(.*?)\"\\]").findAll(json)
        for (match in matches) {
            val k = unescapeJson(match.groupValues[1])
            val v = unescapeJson(match.groupValues[2])
            result.add(Pair(k, v))
        }
        return result
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }

    private fun unescapeJson(str: String): String {
        return str.replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    /**
     * Translates a database [HttpTransactionEntity] to its domain [HttpTransaction] DTO representation.
     *
     * @param entity The database entity to translate.
     * @param payloadStore Disk payload storage store used to load request/response bodies.
     * @return The domain [HttpTransaction] representation.
     */
    fun toDomainModel(entity: HttpTransactionEntity, payloadStore: com.devuloopers.knet.session.FilePayloadStore): HttpTransaction {
        val reqHeaders = deserializeHeaders(entity.requestHeadersJson)
        val reqBody = payloadStore.loadPayload(entity.requestBodyPath)
        val request = HttpRequest(
            id = entity.id,
            method = entity.method,
            url = entity.url,
            protocol = "HTTP/1.1",
            headers = reqHeaders,
            body = reqBody,
            timestamp = entity.timestamp
        )

        val statusCode = entity.responseStatusCode
        val statusText = entity.responseStatusText
        val response = if (statusCode != null && statusText != null) {
            val resHeaders = entity.responseHeadersJson?.let { deserializeHeaders(it) } ?: emptyList()
            val resBody = payloadStore.loadPayload(entity.responseBodyPath)
            HttpResponse(
                statusCode = statusCode,
                statusText = statusText,
                headers = resHeaders,
                body = resBody,
                timestamp = entity.timestamp + entity.durationMs
            )
        } else {
            null
        }

        return HttpTransaction(
            id = entity.id,
            request = request,
            response = response,
            requestBodyPath = entity.requestBodyPath,
            responseBodyPath = entity.responseBodyPath,
            durationMs = entity.durationMs,
            timestamp = entity.timestamp
        )
    }
}
