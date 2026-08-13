package com.devuloopers.knet.data.desktop.mapper

import com.devuloopers.knet.domain.clientNetwork.model.HttpRequest
import com.devuloopers.knet.domain.clientNetwork.model.HttpResponse
import com.devuloopers.knet.domain.clientNetwork.model.HttpTimings
import com.devuloopers.knet.domain.clientNetwork.model.HttpTransaction
import com.devuloopers.knet.storage.traffic.entity.HttpTransactionEntity
import com.devuloopers.knet.domain.protocol.model.InterceptionMetadata

/**
 * Maps between SQLite Room transaction entities and Domain network transaction models.
 */
object TransactionMapper {

    fun mapEntityToDomain(entity: HttpTransactionEntity): HttpTransaction {
        val reqHeadersList = parseHeadersString(entity.requestHeadersJson)
        val resHeadersList = parseHeadersString(entity.responseHeadersJson ?: "")

        val request = HttpRequest(
            id = entity.id,
            method = entity.method,
            url = entity.url,
            protocol = "HTTP/1.1",
            headers = reqHeadersList,
            body = null,
            timestamp = entity.timestamp,
            isIntercepted = entity.isIntercepted,
            matchedRuleId = entity.matchedRuleId
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

        val interceptionMetadata = when (entity.protocolType) {
            "GRAPHQL" -> InterceptionMetadata.GraphQL(
                operationName = entity.graphqlOperationName,
                operationType = entity.graphqlOperationType ?: "Query",
                querySummary = ""
            )
            else -> InterceptionMetadata.GenericHttp
        }

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
            timings = timings,
            interceptionMetadata = interceptionMetadata
        )
    }

    fun mapDomainToEntity(domain: HttpTransaction): HttpTransactionEntity {
        val reqHeadersStr = domain.request.headers.joinToString(";\n") { "${it.first}: ${it.second}" }
        val resHeadersStr = domain.response?.headers?.joinToString(";\n") { "${it.first}: ${it.second}" } ?: ""

        val (protocolType, opName, opType) = when (val meta = domain.interceptionMetadata) {
            is InterceptionMetadata.GraphQL -> Triple("GRAPHQL", meta.operationName, meta.operationType)
            is InterceptionMetadata.Grpc -> Triple("GRPC", null, null)
            is InterceptionMetadata.Protobuf -> Triple("PROTOBUF", null, null)
            else -> Triple("GENERIC_HTTP", null, null)
        }

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
            timingDownloadMs = domain.timings.downloadMs,
            protocolType = protocolType,
            graphqlOperationName = opName,
            graphqlOperationType = opType,
            isIntercepted = domain.request.isIntercepted,
            matchedRuleId = domain.request.matchedRuleId
        )
    }

    private fun parseHeadersString(headersJson: String): List<Pair<String, String>> {
        if (headersJson.isBlank()) return emptyList()
        val trimmed = headersJson.trim()
        if (trimmed.startsWith("[")) {
            return com.devuloopers.knet.engine.session.HttpTransactionMapper.deserializeHeaders(trimmed)
        }
        return trimmed.split(";\n")
            .filter { it.contains(":") }
            .map { line ->
                val parts = line.split(":", limit = 2)
                parts[0].trim() to (parts.getOrNull(1)?.trim() ?: "")
            }
    }
}
