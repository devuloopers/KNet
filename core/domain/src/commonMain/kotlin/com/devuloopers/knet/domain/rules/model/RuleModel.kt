package com.devuloopers.knet.domain.rules.model

import com.devuloopers.knet.domain.protocol.model.InterceptionMetadata

/**
 * Single Source of Truth domain contract representing an active interceptor or modifier rule across KNet.
 *
 * @property id Unique rule identifier.
 * @property name The display name of the rule.
 * @property type The target context type ([RuleType]).
 * @property condition Description of the triggering matching criteria (URL pattern/wildcard).
 * @property action The action execution type or target HTTP method filter (e.g. "ALL", "GET", "POST").
 * @property enabled Whether this rule is currently active.
 * @property protocolCriteria Extensible protocol-specific interception criteria ([ProtocolMatchCriteria]).
 */
data class RuleModel(
    val id: String = "",
    val name: String = "",
    val type: RuleType = RuleType.BOTH,
    val condition: String = "*",
    val action: String = "ALL",
    val enabled: Boolean = true,
    val protocolCriteria: ProtocolMatchCriteria = ProtocolMatchCriteria.HttpDefault
)

/**
 * Single Source of Truth evaluator testing whether this [RuleModel] matches a target HTTP transaction.
 * Shared across Netty Proxy Interceptor Engine and UI Traffic Table row highlighting.
 *
 * @param url Full target URL string or path.
 * @param method HTTP method (e.g. GET, POST).
 * @param currentPhase Target traffic phase filter ([RuleType.REQUEST], [RuleType.RESPONSE], [RuleType.BOTH]).
 * @param requestBodyText Optional decoded request body text for payload scanning.
 * @param metadata Extracted protocol metadata (e.g. [InterceptionMetadata.GraphQL]).
 * @return True if the rule matches the target transaction criteria, false otherwise.
 */
fun RuleModel.matchesTransaction(
    url: String,
    method: String,
    currentPhase: RuleType = RuleType.BOTH,
    requestBodyText: String? = null,
    metadata: InterceptionMetadata? = null
): Boolean {
    if (!enabled) return false

    // 1. Phase check
    if (type != RuleType.BOTH && currentPhase != RuleType.BOTH && type != currentPhase) {
        return false
    }

    // 2. Method check
    val isActionMatched = action.equals("ALL", ignoreCase = true) || action.equals(method, ignoreCase = true)
    if (!isActionMatched) return false

    // 3. URL condition check (Universal Wildcard & High-Performance Regex Safe Matching)
    val isUrlMatched = if (condition.isBlank() || condition == "*" || condition == ".*") {
        true
    } else {
        val safePattern = if (condition.contains("*") && !condition.contains(".*")) {
            condition.replace(".", "\\.").replace("*", ".*")
        } else {
            condition
        }
        val isRegexPattern = safePattern.contains(".*") || safePattern.contains("^") || safePattern.contains("$") || safePattern.startsWith("\\Q")
        if (isRegexPattern) {
            val literalSubstring = safePattern.replace(".*", "").replace("\\.", ".").replace("^", "").replace("$", "")
            if (literalSubstring.length > 3 && !url.contains(literalSubstring, ignoreCase = true)) {
                false
            } else {
                runCatching { Regex(safePattern, RegexOption.IGNORE_CASE).containsMatchIn(url) }.getOrDefault(false)
            }
        } else {
            url.contains(condition, ignoreCase = true)
        }
    }
    if (!isUrlMatched) return false

    // 4. Protocol match criteria check (3-Layer GraphQL Multi-Strategy)
    return when (val criteria = protocolCriteria) {
        is ProtocolMatchCriteria.GraphQL -> {
            val targetOp = criteria.operationName
            if (targetOp.isNullOrBlank()) {
                metadata is InterceptionMetadata.GraphQL || isGraphQlContent(url, requestBodyText)
            } else {
                val metadataMatched = metadata is InterceptionMetadata.GraphQL && metadata.operationName.equals(targetOp, ignoreCase = true)
                val bodyText = requestBodyText.orEmpty()
                val matchesBodyJson = bodyText.contains("\"operationName\":\"$targetOp\"", ignoreCase = true) ||
                        bodyText.contains("\"operationName\": \"$targetOp\"", ignoreCase = true) ||
                        bodyText.contains("\"operationName\":'${targetOp}'", ignoreCase = true)
                val matchesUrlParam = url.contains("operationName=$targetOp", ignoreCase = true)
                val matchesQueryRoot = bodyText.contains("query $targetOp", ignoreCase = true) ||
                        bodyText.contains("mutation $targetOp", ignoreCase = true) ||
                        bodyText.contains("subscription $targetOp", ignoreCase = true)

                metadataMatched || matchesBodyJson || matchesUrlParam || matchesQueryRoot
            }
        }
        is ProtocolMatchCriteria.Grpc -> {
            if (metadata is InterceptionMetadata.Grpc) {
                val serviceMatch = criteria.serviceName.isNullOrBlank() || metadata.serviceName.equals(criteria.serviceName, ignoreCase = true)
                val methodMatch = criteria.methodName.isNullOrBlank() || metadata.methodName.equals(criteria.methodName, ignoreCase = true)
                serviceMatch && methodMatch
            } else {
                false
            }
        }
        is ProtocolMatchCriteria.WebSocket -> true
        ProtocolMatchCriteria.HttpDefault -> true
    }
}

private fun isGraphQlContent(url: String, requestBodyText: String?): Boolean {
    val body = requestBodyText.orEmpty()
    return url.contains("/graphql", ignoreCase = true) ||
            body.contains("\"query\":", ignoreCase = true) ||
            body.contains("query ", ignoreCase = true) ||
            body.contains("mutation ", ignoreCase = true)
}
