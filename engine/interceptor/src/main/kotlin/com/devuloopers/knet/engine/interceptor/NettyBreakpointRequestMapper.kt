package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.engine.proxy.http.ProxyRequestContext
import com.devuloopers.knet.engine.proxy.mapper.HttpMapper
import com.devuloopers.knet.engine.proxy.pipeline.PipelineHandlerNames
import com.devuloopers.knet.engine.proxy.pipeline.ProxyChannelAttributes
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpRequest
import java.net.URI

/** Maps one decoded Netty request to the canonical request context used by breakpoint matching. */
internal fun mapBreakpointRequest(
    context: ChannelHandlerContext,
    request: HttpRequest,
): ProxyRequestContext {
    val absoluteUri = request.uri()
        .takeIf { it.startsWith("http://") || it.startsWith("https://") }
        ?.let { runCatching { URI.create(it) }.getOrNull() }
        ?.takeIf { it.host != null }
    val isSsl = absoluteUri?.scheme?.equals("https", ignoreCase = true) ?:
        (context.channel().attr(ProxyChannelAttributes.IS_SSL).get() == true ||
            context.pipeline().get(PipelineHandlerNames.SSL) != null)
    val authority = absoluteUri?.let { uri ->
        uri.host to when {
            uri.port in 1..65_535 -> uri.port
            isSsl -> 443
            else -> 80
        }
    } ?: resolveBreakpointAuthority(context, request, isSsl)
    return HttpMapper.mapRequestContext(
        nettyReq = request,
        isSsl = isSsl,
        host = authority.first,
        port = authority.second,
        relativeUri = relativeRequestTarget(request.uri()),
    )
}

private fun resolveBreakpointAuthority(
    context: ChannelHandlerContext,
    request: HttpRequest,
    isSsl: Boolean,
): Pair<String, Int> {
    val tunneledHost = context.channel().attr(ProxyChannelAttributes.HOST).get()
    if (!tunneledHost.isNullOrBlank()) {
        val tunneledPort = context.channel().attr(ProxyChannelAttributes.PORT).get()
        return tunneledHost to (tunneledPort ?: if (isSsl) 443 else 80)
    }
    val hostHeader = request.headers()["Host"].orEmpty()
    val defaultPort = if (isSsl) 443 else 80
    val bracketEnd = hostHeader.indexOf(']')
    return if (hostHeader.startsWith('[') && bracketEnd > 0) {
        val host = hostHeader.substring(1, bracketEnd)
        host to (hostHeader.substring(bracketEnd + 1).removePrefix(":").toIntOrNull() ?: defaultPort)
    } else {
        val possiblePort = hostHeader.substringAfterLast(':', "").toIntOrNull()
        val host = if (possiblePort == null) hostHeader else hostHeader.substringBeforeLast(':')
        host.ifBlank { "unknown" } to (possiblePort ?: defaultPort)
    }
}

private fun relativeRequestTarget(uri: String): String =
    if (uri.startsWith("http://") || uri.startsWith("https://")) {
        runCatching {
            val parsed = URI.create(uri)
            val path = parsed.rawPath?.ifBlank { "/" } ?: "/"
            parsed.rawQuery?.let { "$path?$it" } ?: path
        }.getOrDefault(uri)
    } else {
        uri
    }
