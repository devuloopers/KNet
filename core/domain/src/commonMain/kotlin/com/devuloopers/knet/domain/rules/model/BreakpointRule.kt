package com.devuloopers.knet.domain.rules.model

import com.devuloopers.knet.domain.protocol.model.InterceptionMetadata
import com.devuloopers.knet.traffic.model.http.HttpMethod

/**
 * Canonical authored breakpoint rule shared by rule management, persistence, highlighting, and
 * the application breakpoint coordinator.
 */
data class BreakpointRule(
    val id: String = "",
    val name: String = "",
    val urlPattern: String = "*",
    val method: HttpMethod? = null,
    val phase: BreakpointPhase = BreakpointPhase.BOTH,
    val enabled: Boolean = true,
    val protocolCriteria: ProtocolMatchCriteria = ProtocolMatchCriteria.HttpDefault,
) {
    init {
        require(id.isNotBlank()) { "Breakpoint rule ID must not be blank." }
        require(urlPattern.isNotBlank()) { "Breakpoint URL pattern must not be blank." }
    }
}

/** Tests whether this rule matches one protocol-aware transaction observation. */
fun BreakpointRule.matchesTransaction(
    url: String,
    method: String,
    currentPhase: BreakpointPhase = BreakpointPhase.BOTH,
    requestBodyText: String? = null,
    metadata: InterceptionMetadata? = null,
): Boolean {
    if (!enabled) return false
    if (phase != BreakpointPhase.BOTH && currentPhase != BreakpointPhase.BOTH && phase != currentPhase) {
        return false
    }
    if (this.method != null && !this.method.token.equals(method, ignoreCase = true)) return false
    if (!urlMatches(urlPattern, url)) return false

    return when (val criteria = protocolCriteria) {
        is ProtocolMatchCriteria.GraphQL -> {
            val operationName = criteria.operationName
            if (operationName.isNullOrBlank()) {
                metadata is InterceptionMetadata.GraphQL || isGraphQlContent(url, requestBodyText)
            } else {
                val metadataMatched = metadata is InterceptionMetadata.GraphQL &&
                    metadata.operationName.equals(operationName, ignoreCase = true)
                val bodyText = requestBodyText.orEmpty()
                val matchesBodyJson = bodyText.contains("\"operationName\":\"$operationName\"", ignoreCase = true) ||
                    bodyText.contains("\"operationName\": \"$operationName\"", ignoreCase = true) ||
                    bodyText.contains("\"operationName\":'$operationName'", ignoreCase = true)
                val matchesUrlParam = url.contains("operationName=$operationName", ignoreCase = true)
                val matchesQueryRoot = bodyText.contains("query $operationName", ignoreCase = true) ||
                    bodyText.contains("mutation $operationName", ignoreCase = true) ||
                    bodyText.contains("subscription $operationName", ignoreCase = true)
                metadataMatched || matchesBodyJson || matchesUrlParam || matchesQueryRoot
            }
        }

        is ProtocolMatchCriteria.Grpc -> {
            metadata is InterceptionMetadata.Grpc &&
                (criteria.serviceName.isNullOrBlank() ||
                    metadata.serviceName.equals(criteria.serviceName, ignoreCase = true)) &&
                (criteria.methodName.isNullOrBlank() ||
                    metadata.methodName.equals(criteria.methodName, ignoreCase = true))
        }

        is ProtocolMatchCriteria.WebSocket -> true
        ProtocolMatchCriteria.HttpDefault -> true
    }
}

private fun urlMatches(pattern: String, url: String): Boolean {
    if (pattern == "*" || pattern == ".*") return true
    val expression = if ('*' in pattern && ".*" !in pattern) {
        pattern.split('*').joinToString(".*") { Regex.escape(it) }
    } else {
        pattern
    }
    return runCatching { Regex(expression, RegexOption.IGNORE_CASE).containsMatchIn(url) }
        .getOrElse { url.contains(pattern, ignoreCase = true) }
}

private fun isGraphQlContent(url: String, requestBodyText: String?): Boolean {
    val body = requestBodyText.orEmpty()
    return url.contains("/graphql", ignoreCase = true) ||
        body.contains("\"query\":", ignoreCase = true) ||
        body.contains("query ", ignoreCase = true) ||
        body.contains("mutation ", ignoreCase = true)
}
