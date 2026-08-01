package com.devuloopers.knet.data.desktop.mapper

import com.devuloopers.knet.domain.network.model.HttpRequest
import com.devuloopers.knet.domain.network.model.HttpResponse
import com.devuloopers.knet.domain.network.model.HttpTransaction
import com.devuloopers.knet.storage.traffic.entity.HttpTransactionEntity

/**
 * Maps between SQLite Room transaction entities and Domain network transaction models.
 */
public object TransactionMapper {

    public fun mapEntityToDomain(entity: HttpTransactionEntity): HttpTransaction {
        val reqHeadersList = parseHeadersString(entity.requestHeadersJson)
        val resHeadersList = parseHeadersString(entity.responseHeadersJson ?: "")

        val request = HttpRequest(
            id = entity.id,
            method = entity.method,
            url = entity.url,
            protocol = "HTTP/1.1",
            headers = reqHeadersList,
            body = null,
            timestamp = entity.timestamp
        )

        val response = HttpResponse(
            statusCode = entity.responseStatusCode ?: 0,
            statusText = entity.responseStatusText ?: "",
            headers = resHeadersList,
            body = null,
            timestamp = entity.timestamp
        )

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

    private fun parseHeadersString(headersJson: String): List<Pair<String, String>> {
        if (headersJson.isBlank()) return emptyList()
        return headersJson.split(";\n")
            .filter { it.contains(":") }
            .map { line ->
                val parts = line.split(":", limit = 2)
                parts[0].trim() to (parts.getOrNull(1)?.trim() ?: "")
            }
    }
}
