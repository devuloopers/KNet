package com.devuloopers.knet.engine.traffic.processors

import com.devuloopers.knet.engine.traffic.MapLocalRule
import com.devuloopers.knet.engine.traffic.RegexCache
import com.devuloopers.knet.core.logger.KNetLogger
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.util.ReferenceCountUtil
import java.io.File
import java.nio.file.Files

private const val TAG = "MapLocalProcessor"

internal object MapLocalProcessor {

    /**
     * Evaluates active Map Local rules against the request URL. If matched, serves
     * the local file directly as a synthetic HTTP response and returns true.
     *
     * @return True if a Map Local rule matched and handled the request, false otherwise.
     */
    fun process(
        context: ChannelHandlerContext,
        request: FullHttpRequest,
        url: String,
        rules: List<MapLocalRule>
    ): Boolean {
        val matchedRule = rules.firstOrNull { rule ->
            rule.enabled && RegexCache.getOrNull(rule.urlPattern)?.containsMatchIn(url) == true
        } ?: return false

        KNetLogger.debug(TAG) { "Map Local matched [${matchedRule.id}]: serving ${matchedRule.localFilePath}" }
        serveLocalFile(context, request, matchedRule)
        return true
    }

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
}
