package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.application.port.breakpoint.BreakpointResponseEdit
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpResponseStatus

/**
 * Converts one canonical bounded response edit back to a Netty full response.
 */
object ResponseRebuilder {

    fun rebuild(original: FullHttpResponse, edit: BreakpointResponseEdit): FullHttpResponse {
        val body = edit.body?.copyBytes()
        val content = if (body != null) {
            Unpooled.copiedBuffer(body)
        } else {
            Unpooled.EMPTY_BUFFER
        }

        val rebuilt = DefaultFullHttpResponse(
            original.protocolVersion(),
            HttpResponseStatus.valueOf(edit.response.head.status.code),
            content
        )

        rebuilt.headers().clear()
        edit.response.head.headers.forEach { header ->
            rebuilt.headers().add(header.name.value, header.value)
        }
        rebuilt.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes())
        return rebuilt
    }
}
