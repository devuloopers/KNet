package com.devuloopers.knet.engine.traffic.processors

import com.devuloopers.knet.engine.traffic.MimeTypeUtils
import com.devuloopers.knet.engine.traffic.ModifierRule
import com.devuloopers.knet.engine.traffic.RegexCache
import com.devuloopers.knet.engine.traffic.RuleAction
import com.devuloopers.knet.engine.traffic.RuleTarget
import com.devuloopers.knet.core.logger.KNetLogger
import io.netty.buffer.ByteBuf
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpResponseStatus

private const val TAG = "ResponseModifierProcessor"

internal object ResponseModifierProcessor {

    /**
     * Evaluates and applies response-side modifier rules (headers, status codes, body text) to an outbound response frame.
     */
    fun process(response: FullHttpResponse, rules: List<ModifierRule>) {
        val applicableRules = rules.filter { rule ->
            rule.enabled && rule.target in listOf(
                RuleTarget.RESPONSE_HEADER,
                RuleTarget.RESPONSE_BODY,
                RuleTarget.RESPONSE_STATUS
            )
        }

        for (rule in applicableRules) {
            when (rule.target) {
                RuleTarget.RESPONSE_HEADER -> applyHeaderRule(response.headers(), rule)
                RuleTarget.RESPONSE_STATUS -> applyStatusRule(response, rule)
                RuleTarget.RESPONSE_BODY -> applyBodyRule(response, rule)
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
                    KNetLogger.debug(TAG) { "Response header added: $matchKey = ${rule.newValue}" }
                }
            }
            RuleAction.MODIFY -> {
                if (rule.newValue != null) {
                    headers.set(matchKey, rule.newValue)
                    KNetLogger.debug(TAG) { "Response header modified: $matchKey = ${rule.newValue}" }
                }
            }
            RuleAction.REMOVE -> {
                headers.remove(matchKey)
                KNetLogger.debug(TAG) { "Response header removed: $matchKey" }
            }
        }
    }

    private fun applyStatusRule(response: FullHttpResponse, rule: ModifierRule) {
        val code = rule.newValue?.toIntOrNull()
        if (code != null) {
            response.status = HttpResponseStatus.valueOf(code)
            KNetLogger.debug(TAG) { "Response status overridden to $code" }
        }
    }

    private fun applyBodyRule(response: FullHttpResponse, rule: ModifierRule) {
        val contentType = response.headers().get(HttpHeaderNames.CONTENT_TYPE)
        if (!MimeTypeUtils.isTextualPayload(contentType)) {
            KNetLogger.debug(TAG) { "Skipping binary response body mutation ($contentType)" }
            return
        }

        replaceBodyContent(response.content(), response.headers(), rule.matchValue, rule.newValue)
        KNetLogger.debug(TAG) { "Response body modified [rule: ${rule.id}]" }
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
