package com.devuloopers.knet.modifier

import com.devuloopers.knet.logger.KNetLogger
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.util.AttributeKey
import io.netty.util.ReferenceCountUtil
import java.io.File
import java.nio.file.Files

private const val TAG = "KNetTrafficModifierHandler"

/**
 * Netty [ChannelDuplexHandler] that automatically applies active traffic modification rules.
 *
 * This handler must be registered **before** `proxyHandler` in the server pipeline so that
 * modifications are applied before the proxy engine forwards them upstream.
 *
 * Supported operations:
 * - **Map Local**: Serve a local file response immediately without hitting the network.
 * - **Map Remote**: Transparently re-route to an alternate target host and port.
 * - **Modifier Rules**: Mutate headers, query parameters, body content, or response status codes.
 *
 * @property manager The [TrafficModifierManager] containing active rule sets.
 */
class KNetTrafficModifierHandler(
    private val manager: TrafficModifierManager
) : ChannelDuplexHandler() {

    companion object {
        /** Channel attribute key storing the target hostname for the proxy engine. */
        val HOST_ATTR: AttributeKey<String> = AttributeKey.valueOf("knet.host")

        /** Channel attribute key storing the target port for the proxy engine. */
        val PORT_ATTR: AttributeKey<Int> = AttributeKey.valueOf("knet.port")
    }

    /**
     * Intercepts inbound HTTP requests. Applies Map Local short-circuit, Map Remote re-routing,
     * and request-side Modifier Rules before forwarding the request downstream.
     */
    override fun channelRead(context: ChannelHandlerContext, msg: Any) {
        if (msg !is FullHttpRequest) {
            context.fireChannelRead(msg)
            return
        }

        val url = resolveFullUrl(context, msg)
        KNetLogger.debug(TAG) { "Evaluating traffic modifier rules for: $url" }

        // --- 1. Map Local: Short-circuit with a local file response ---
        val mapLocalRule = manager.mapLocalRules
            .firstOrNull { it.enabled && url.contains(Regex(it.urlPattern)) }

        if (mapLocalRule != null) {
            KNetLogger.debug(TAG) { "Map Local matched [${mapLocalRule.id}]: serving ${mapLocalRule.localFilePath}" }
            serveLocalFile(context, msg, mapLocalRule)
            return // Do NOT forward to proxyHandler
        }

        // --- 2. Map Remote: Re-route to alternate host ---
        val mapRemoteRule = manager.mapRemoteRules
            .firstOrNull { it.enabled && url.contains(Regex(it.urlPattern)) }

        if (mapRemoteRule != null) {
            KNetLogger.debug(TAG) { "Map Remote matched [${mapRemoteRule.id}]: redirecting to ${mapRemoteRule.targetHost}:${mapRemoteRule.targetPort}" }
            context.channel().attr(HOST_ATTR).set(mapRemoteRule.targetHost)
            context.channel().attr(PORT_ATTR).set(mapRemoteRule.targetPort)
            msg.headers().set(HttpHeaderNames.HOST, mapRemoteRule.targetHost)
        }

        // --- 3. Apply request-side Modifier Rules ---
        val requestRules = manager.modifierRules.filter { rule ->
            rule.enabled && url.contains(Regex(rule.urlPattern)) &&
                rule.target in listOf(
                    RuleTarget.REQUEST_HEADER,
                    RuleTarget.REQUEST_QUERY,
                    RuleTarget.REQUEST_BODY
                )
        }
        applyRequestRules(msg, requestRules)

        context.fireChannelRead(msg)
    }

    /**
     * Intercepts outbound HTTP responses. Applies response-side Modifier Rules before
     * the response is written back to the client.
     */
    override fun write(context: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        if (msg !is FullHttpResponse) {
            context.write(msg, promise)
            return
        }

        val responseRules = manager.modifierRules.filter { rule ->
            rule.enabled && rule.target in listOf(
                RuleTarget.RESPONSE_HEADER,
                RuleTarget.RESPONSE_BODY,
                RuleTarget.RESPONSE_STATUS
            )
        }
        applyResponseRules(msg, responseRules)

        context.write(msg, promise)
    }

    /**
     * Reads the local file from [MapLocalRule.localFilePath] and writes a synthetic
     * HTTP 200 response back to the client channel, bypassing the upstream proxy entirely.
     *
     * @param context The current Netty channel handler context.
     * @param request The original inbound HTTP request (released after use).
     * @param rule The matched Map Local rule.
     */
    private fun serveLocalFile(
        context: ChannelHandlerContext,
        request: FullHttpRequest,
        rule: MapLocalRule
    ) {
        val file = File(rule.localFilePath)
        val bytes = if (file.exists()) {
            file.readBytes()
        } else {
            KNetLogger.error(TAG) { "Map Local file not found: ${rule.localFilePath}" }
            ByteArray(0)
        }

        val mimeType = rule.mimeType
            ?: runCatching { Files.probeContentType(file.toPath()) }.getOrNull()
            ?: "application/octet-stream"

        val status = if (file.exists()) HttpResponseStatus.OK else HttpResponseStatus.NOT_FOUND
        val buf = Unpooled.wrappedBuffer(bytes)
        val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buf)
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, mimeType)
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bytes.size)
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)

        ReferenceCountUtil.release(request)
        context.writeAndFlush(response)
    }

    /**
     * Reconstructs the full URL from channel attributes and request URI.
     * Handles both plain-text and tunnel (TLS) request URI forms.
     */
    private fun resolveFullUrl(context: ChannelHandlerContext, msg: FullHttpRequest): String {
        val uri = msg.uri()
        if (uri.startsWith("http://") || uri.startsWith("https://")) return uri
        val host = context.channel().attr(HOST_ATTR).get() ?: ""
        val port = context.channel().attr(PORT_ATTR).get() ?: 443
        val isSsl = port == 443 || port == 8443
        val scheme = if (isSsl) "https" else "http"
        return "$scheme://$host$uri"
    }

    /**
     * Applies all matching request-side [ModifierRule] entries to the given [FullHttpRequest],
     * performing header additions/removals and body text replacements inline.
     *
     * @param request The inbound request frame to mutate.
     * @param rules The set of applicable modifier rules.
     */
    private fun applyRequestRules(request: FullHttpRequest, rules: List<ModifierRule>) {
        for (rule in rules) {
            when (rule.target) {
                RuleTarget.REQUEST_HEADER -> applyHeaderRule(
                    headers = request.headers(),
                    action = rule.action,
                    matchValue = rule.matchValue,
                    newValue = rule.newValue
                )
                RuleTarget.REQUEST_BODY -> applyBodyRule(request, rule)
                else -> Unit
            }
        }
    }

    /**
     * Applies all matching response-side [ModifierRule] entries to the given [FullHttpResponse],
     * performing header changes, status overrides, and body text replacements inline.
     *
     * @param response The outbound response frame to mutate.
     * @param rules The set of applicable modifier rules.
     */
    private fun applyResponseRules(response: FullHttpResponse, rules: List<ModifierRule>) {
        for (rule in rules) {
            when (rule.target) {
                RuleTarget.RESPONSE_HEADER -> applyHeaderRule(
                    headers = response.headers(),
                    action = rule.action,
                    matchValue = rule.matchValue,
                    newValue = rule.newValue
                )
                RuleTarget.RESPONSE_STATUS -> {
                    val code = rule.newValue?.toIntOrNull()
                    if (code != null) {
                        response.status = HttpResponseStatus.valueOf(code)
                        KNetLogger.debug(TAG) { "Response status overridden to $code" }
                    }
                }
                RuleTarget.RESPONSE_BODY -> {
                    replaceBodyContent(
                        content = response.content(),
                        headers = response.headers(),
                        matchValue = rule.matchValue,
                        newValue = rule.newValue
                    )
                }
                else -> Unit
            }
        }
    }

    /**
     * Applies a header-level ADD, MODIFY (replace), or REMOVE action.
     *
     * @param headers The HTTP header set to mutate.
     * @param action ADD, MODIFY, or REMOVE.
     * @param matchValue The header name to match.
     * @param newValue The value to set for ADD/MODIFY.
     */
    private fun applyHeaderRule(
        headers: io.netty.handler.codec.http.HttpHeaders,
        action: RuleAction,
        matchValue: String?,
        newValue: String?
    ) {
        when (action) {
            RuleAction.ADD -> {
                if (matchValue != null && newValue != null) {
                    headers.add(matchValue, newValue)
                    KNetLogger.debug(TAG) { "Header added: $matchValue = $newValue" }
                }
            }
            RuleAction.MODIFY -> {
                if (matchValue != null && newValue != null) {
                    headers.set(matchValue, newValue)
                    KNetLogger.debug(TAG) { "Header modified: $matchValue = $newValue" }
                }
            }
            RuleAction.REMOVE -> {
                if (matchValue != null) {
                    headers.remove(matchValue)
                    KNetLogger.debug(TAG) { "Header removed: $matchValue" }
                }
            }
        }
    }

    /**
     * Applies a body-text modification rule to a [FullHttpRequest] frame.
     * Replaces matching regex patterns with the specified replacement text.
     *
     * @param request The request frame whose body content will be mutated.
     * @param rule The modifier rule containing regex match and replacement values.
     */
    private fun applyBodyRule(request: FullHttpRequest, rule: ModifierRule) {
        replaceBodyContent(
            content = request.content(),
            headers = request.headers(),
            matchValue = rule.matchValue,
            newValue = rule.newValue
        )
        KNetLogger.debug(TAG) { "Request body modified" }
    }

    /**
     * Shared helper that performs an in-place body text replacement on any Netty [io.netty.buffer.ByteBuf].
     * Reads the current buffer as UTF-8, applies a regex replacement if [matchValue] is provided,
     * then clears and rewrites the buffer, and updates the Content-Length header.
     *
     * @param content The ByteBuf to mutate in place.
     * @param headers The HTTP header set to update with the new content length.
     * @param matchValue Optional regex pattern to search for. If null, the content is unchanged.
     * @param newValue Replacement text. Defaults to empty string if null.
     */
    private fun replaceBodyContent(
        content: io.netty.buffer.ByteBuf,
        headers: io.netty.handler.codec.http.HttpHeaders,
        matchValue: String?,
        newValue: String?
    ) {
        val bodyText = content.toString(Charsets.UTF_8)
        val modified = matchValue?.let { bodyText.replace(Regex(it), newValue ?: "") } ?: bodyText
        content.clear()
        content.writeBytes(modified.toByteArray(Charsets.UTF_8))
        headers.set(HttpHeaderNames.CONTENT_LENGTH, modified.length)
    }
}
