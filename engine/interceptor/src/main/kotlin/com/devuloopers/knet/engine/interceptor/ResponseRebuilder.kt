package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.domain.clientNetwork.model.HttpResponse
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpResponseStatus

/**
 * Utility converting an edited common HttpResponse DTO back to Netty's FullHttpResponse frame.
 */
object ResponseRebuilder {

    fun rebuild(original: FullHttpResponse, modified: HttpResponse): FullHttpResponse {
        val content = if (modified.body != null) {
            Unpooled.copiedBuffer(modified.body)
        } else {
            Unpooled.EMPTY_BUFFER
        }

        val rebuilt = DefaultFullHttpResponse(
            original.protocolVersion(),
            HttpResponseStatus.valueOf(modified.statusCode),
            content
        )

        rebuilt.headers().clear()
        modified.headers.forEach { (key, value) ->
            rebuilt.headers().add(key, value)
        }
        rebuilt.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes())
        return rebuilt
    }
}
