package com.devuloopers.knet.domain.rules.model

/**
 * Target HTTP message context for an interceptor or rewrite rule.
 */
enum class RuleType(val displayName: String) {
    REQUEST("Request"),
    RESPONSE("Response")
}
