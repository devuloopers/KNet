package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.application.contract.breakpoint.BreakpointBodyEdit
import com.devuloopers.knet.application.contract.breakpoint.BreakpointResponseEdit
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus

/**
 * Converts one canonical bounded response edit back to a Netty full response.
 */
object ResponseRebuilder {

    /**
     * Rebuilds [original] from a validated canonical [edit].
     *
     * @param requestMethod Original request method used to enforce HEAD response semantics.
     * @return A newly owned full response that the caller must forward or release.
     */
    fun rebuild(
        original: FullHttpResponse,
        edit: BreakpointResponseEdit,
        requestMethod: HttpMethod? = null,
    ): FullHttpResponse {
        val statusCode = edit.response.head.status.code
        val metadataOnlyResponse = requestMethod == HttpMethod.HEAD || statusCode == 304
        val forbidsBody = metadataOnlyResponse || statusCode in 100..199 || statusCode == 204
        val content = when {
            forbidsBody -> Unpooled.EMPTY_BUFFER
            edit.body == BreakpointBodyEdit.Unchanged -> original.content().retainedDuplicate()
            else -> Unpooled.copiedBuffer((edit.body as BreakpointBodyEdit.Replace).body.copyBytes())
        }
        val defaultStatus = HttpResponseStatus.valueOf(statusCode)
        val status = HttpResponseStatus(
            statusCode,
            edit.response.head.reasonPhrase?.takeIf(String::isNotBlank) ?: defaultStatus.reasonPhrase(),
        )

        val rebuilt = DefaultFullHttpResponse(
            original.protocolVersion(),
            status,
            content,
        )
        val preserveTrailers = !forbidsBody && edit.body == BreakpointBodyEdit.Unchanged &&
            !original.trailingHeaders().isEmpty

        rebuilt.headers().replaceWithFullMessageHeaders(
            headers = edit.response.head.headers,
            contentLength = when {
                metadataOnlyResponse -> edit.response.head.headers
                    .firstOrNull { it.name.value.equals(HttpHeaderNames.CONTENT_LENGTH.toString(), true) }
                    ?.value
                    ?.toLongOrNull()
                forbidsBody -> null
                else -> content.readableBytes().toLong()
            },
            trailerNames = if (preserveTrailers) original.trailingHeaders().names() else emptySet(),
        )
        if (preserveTrailers) {
            rebuilt.trailingHeaders().set(original.trailingHeaders())
        }
        return rebuilt
    }
}
