package com.devuloopers.knet.data.desktop.mapper

import com.devuloopers.knet.domain.clientNetwork.model.HttpRequest
import com.devuloopers.knet.domain.clientNetwork.model.HttpResponse
import com.devuloopers.knet.domain.clientNetwork.model.HttpTimings
import com.devuloopers.knet.domain.clientNetwork.model.HttpTransaction
import com.devuloopers.knet.storage.traffic.entity.HttpTransactionEntity
import java.io.File

/**
 * Maps between SQLite Room transaction entities and Domain network transaction models.
 */
public object TransactionMapper {

    public fun mapEntityToDomain(entity: HttpTransactionEntity): HttpTransaction {
        val reqHeadersList = parseHeadersString(entity.requestHeadersJson)
        val resHeadersList = parseHeadersString(entity.responseHeadersJson ?: "")

        // Body bytes are intentionally NOT read here. Payload files are loaded on-demand
        // via LiveTrafficRepository.loadTransactionBody() when the user opens the inspector.
        val request = HttpRequest(
            id = entity.id,
            method = entity.method,
            url = entity.url,
            protocol = "HTTP/1.1",
            headers = reqHeadersList,
            body = null,
            timestamp = entity.timestamp
        )

        val statusCode = entity.responseStatusCode
        val response = if (statusCode != null) {
            HttpResponse(
                statusCode = statusCode,
                statusText = entity.responseStatusText ?: "",
                headers = resHeadersList,
                body = null,
                timestamp = entity.timestamp
            )
        } else {
            null
        }

        val timings = HttpTimings(
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
            requestBodySize = entity.requestBodySize,
            responseBodySize = entity.responseBodySize,
            durationMs = entity.durationMs,
            timestamp = entity.timestamp,
            timings = timings
        )
    }

    public fun mapDomainToEntity(domain: HttpTransaction): HttpTransactionEntity {
        val reqHeadersStr = domain.request.headers.joinToString(";\n") { "${it.first}: ${it.second}" }
        val resHeadersStr = domain.response?.headers?.joinToString(";\n") { "${it.first}: ${it.second}" } ?: ""

        return HttpTransactionEntity(
            id = domain.id,
            url = domain.request.url,
            method = domain.request.method,
            requestHeadersJson = reqHeadersStr,
            requestBodyPath = domain.requestBodyPath,
            requestBodySize = domain.requestBodySize,
            responseStatusCode = domain.response?.statusCode,
            responseStatusText = domain.response?.statusText,
            responseHeadersJson = resHeadersStr,
            responseBodyPath = domain.responseBodyPath,
            responseBodySize = domain.responseBodySize,
            durationMs = domain.durationMs,
            timestamp = domain.timestamp,
            timingDnsMs = domain.timings.dnsMs,
            timingTcpMs = domain.timings.tcpMs,
            timingTlsMs = domain.timings.tlsMs,
            timingTtfbMs = domain.timings.ttfbMs,
            timingDownloadMs = domain.timings.downloadMs
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

    private fun readBodyFromPath(path: String?): ByteArray? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        return if (file.exists() && file.length() > 0) {
            try {
                file.readBytes()
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }
}
