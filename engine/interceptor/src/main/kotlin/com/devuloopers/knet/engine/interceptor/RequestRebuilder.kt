package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.domain.clientNetwork.model.HttpRequest
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import java.net.URI

/**
 * Utility converting an edited common HttpRequest DTO back to Netty's FullHttpRequest frame.
 */
object RequestRebuilder {

    fun rebuild(original: FullHttpRequest, modified: HttpRequest): FullHttpRequest {
        val content = if (modified.body != null) {
            Unpooled.copiedBuffer(modified.body)
        } else {
            Unpooled.EMPTY_BUFFER
        }

        val rebuilt = DefaultFullHttpRequest(
            original.protocolVersion(),
            HttpMethod.valueOf(modified.method),
            extractRelativeUri(modified.url),
            content
        )

        rebuilt.headers().clear()
        modified.headers.forEach { (key, value) ->
            rebuilt.headers().add(key, value)
        }
        rebuilt.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes())
        return rebuilt
    }

    private fun extractRelativeUri(urlStr: String): String {
        return if (urlStr.startsWith("http://") || urlStr.startsWith("https://")) {
            try {
                val uri = URI.create(urlStr)
                val path = uri.path.ifEmpty { "/" }
                val query = uri.rawQuery
                if (query != null) "$path?$query" else path
            } catch (_: Exception) {
                urlStr
            }
        } else {
            urlStr
        }
    }
}
