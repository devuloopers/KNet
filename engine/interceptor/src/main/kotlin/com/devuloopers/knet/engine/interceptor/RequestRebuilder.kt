package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.application.port.breakpoint.BreakpointRequestEdit
import com.devuloopers.knet.traffic.model.http.RequestTarget
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod

/**
 * Converts one canonical bounded request edit back to a Netty full request.
 */
object RequestRebuilder {

    fun rebuild(original: FullHttpRequest, edit: BreakpointRequestEdit): FullHttpRequest {
        val body = edit.body?.copyBytes()
        val content = if (body != null) {
            Unpooled.copiedBuffer(body)
        } else {
            Unpooled.EMPTY_BUFFER
        }

        val rebuilt = DefaultFullHttpRequest(
            original.protocolVersion(),
            HttpMethod.valueOf(edit.request.head.method.token),
            relativeTarget(edit.request.head.target),
            content
        )

        rebuilt.headers().clear()
        edit.request.head.headers.forEach { header ->
            rebuilt.headers().add(header.name.value, header.value)
        }
        rebuilt.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes())
        return rebuilt
    }

    private fun relativeTarget(target: RequestTarget): String = when (target) {
        is RequestTarget.Absolute -> target.pathAndQuery
        is RequestTarget.Origin -> target.pathAndQuery
        is RequestTarget.AuthorityForm -> target.authority.host + target.authority.port?.let { ":$it" }.orEmpty()
        RequestTarget.Asterisk -> "*"
        is RequestTarget.Custom -> target.value
    }
}
