package com.devuloopers.knet.engine.session

import com.devuloopers.knet.domain.network.model.HttpRequest
import com.devuloopers.knet.domain.network.model.HttpResponse
import com.devuloopers.knet.domain.network.model.HttpTransaction
import com.devuloopers.knet.storage.traffic.entity.HttpTransactionEntity

/**
 * Mapper translating database entities to domain models and serializing header lists.
 */
object HttpTransactionMapper {

    /**
     * Serializes HTTP header key-value pairs into a JSON array format `[["k","v"]]`.
     */
    fun serializeHeaders(headers: List<Pair<String, String>>): String {
        return headers.joinToString(separator = ",", prefix = "[", postfix = "]") { (k, v) ->
            "[\"${escapeJson(k)}\",\"${escapeJson(v)}\"]"
        }
    }

    /**
     * Deserializes JSON header array string back to a list of header key-value pairs.
     */
    fun deserializeHeaders(json: String): List<Pair<String, String>> {
        if (json == "[]" || json.isEmpty()) return emptyList()
        val result = mutableListOf<Pair<String, String>>()
        val matches = Regex("\\[\"((?:[^\"]|\\\\.)*)\",\"((?:[^\"]|\\\\.)*)\"]").findAll(json)
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
     * Maps database [HttpTransactionEntity] to domain [HttpTransaction] representation.
     */
    fun toDomainModel(entity: HttpTransactionEntity, payloadStore: FilePayloadStore): HttpTransaction {
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

        val timings = com.devuloopers.knet.domain.network.model.HttpTimings(
            dnsMs = entity.timingDnsMs,
            tcpMs = entity.timingTcpMs,
            tlsMs = entity.timingTlsMs,
            ttfbMs = entity.timingTtfbMs,
            downloadMs = entity.timingDownloadMs
        )

        return HttpTransaction(
            id = entity.id,
            request = request,
            response = response,
            requestBodyPath = entity.requestBodyPath,
            responseBodyPath = entity.responseBodyPath,
            durationMs = entity.durationMs,
            timestamp = entity.timestamp,
            timings = timings
        )
    }
}
