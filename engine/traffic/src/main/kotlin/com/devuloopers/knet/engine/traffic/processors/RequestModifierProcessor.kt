package com.devuloopers.knet.engine.traffic.processors

import com.devuloopers.knet.engine.traffic.MimeTypeUtils
import com.devuloopers.knet.engine.traffic.ModifierRule
import com.devuloopers.knet.engine.traffic.RegexCache
import com.devuloopers.knet.engine.traffic.RuleAction
import com.devuloopers.knet.engine.traffic.RuleTarget
import com.devuloopers.knet.core.logger.KNetLogger
import io.netty.buffer.ByteBuf
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaders

private const val TAG = "RequestModifierProcessor"

internal object RequestModifierProcessor {

    /**
     * Evaluates and applies request-side modifier rules (headers, body text) to an inbound request frame.
     */
    fun process(request: FullHttpRequest, url: String, rules: List<ModifierRule>) {
        val applicableRules = rules.filter { rule ->
            rule.enabled &&
                    rule.target in listOf(RuleTarget.REQUEST_HEADER, RuleTarget.REQUEST_QUERY, RuleTarget.REQUEST_BODY) &&
                    RegexCache.getOrNull(rule.urlPattern)?.containsMatchIn(url) == true
        }

        for (rule in applicableRules) {
            when (rule.target) {
                RuleTarget.REQUEST_HEADER -> applyHeaderRule(request.headers(), rule)
                RuleTarget.REQUEST_BODY -> applyBodyRule(request, rule)
                else -> Unit
            }
        }
    }

    private fun applyHeaderRule(headers: HttpHeaders, rule: ModifierRule) {
        val matchKey = rule.matchValue ?: return
        when (rule.action) {
            RuleAction.ADD -> {
                if (rule.newValue != null) {
                    headers.add(matchKey, rule.newValue)
                    KNetLogger.debug(TAG) { "Request header added: $matchKey = ${rule.newValue}" }
                }
            }
            RuleAction.MODIFY -> {
                if (rule.newValue != null) {
                    headers.set(matchKey, rule.newValue)
                    KNetLogger.debug(TAG) { "Request header modified: $matchKey = ${rule.newValue}" }
                }
            }
            RuleAction.REMOVE -> {
                headers.remove(matchKey)
                KNetLogger.debug(TAG) { "Request header removed: $matchKey" }
            }
        }
    }

    private fun applyBodyRule(request: FullHttpRequest, rule: ModifierRule) {
        val contentType = request.headers().get(HttpHeaderNames.CONTENT_TYPE)
        if (!MimeTypeUtils.isTextualPayload(contentType)) {
            KNetLogger.debug(TAG) { "Skipping binary request body mutation ($contentType)" }
            return
        }

        replaceBodyContent(request.content(), request.headers(), rule.matchValue, rule.newValue)
        KNetLogger.debug(TAG) { "Request body modified [rule: ${rule.id}]" }
    }

    private fun replaceBodyContent(
        content: ByteBuf,
        headers: HttpHeaders,
        matchValue: String?,
        newValue: String?
    ) {
        val bodyText = content.toString(Charsets.UTF_8)
        val regex = matchValue?.let { RegexCache.getOrNull(it) }
        val modified = if (regex != null) {
            bodyText.replace(regex, newValue ?: "")
        } else {
            bodyText
        }
        content.clear()
        content.writeBytes(modified.toByteArray(Charsets.UTF_8))
        headers.set(HttpHeaderNames.CONTENT_LENGTH, modified.length)
    }
}
