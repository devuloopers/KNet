package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.application.contract.breakpoint.BreakpointBodyEdit
import com.devuloopers.knet.application.contract.breakpoint.BreakpointRequestEdit
import com.devuloopers.knet.traffic.model.http.RequestTarget
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod

/**
 * Converts one canonical bounded request edit back to a Netty full request.
 */
object RequestRebuilder {

    /**
     * Rebuilds [original] from a validated canonical [edit].
     *
     * An unchanged body retains the original buffer and trailers. A replacement copies the
     * application-owned bytes into a new Netty buffer. Framing is normalized in both cases.
     *
     * @return A newly owned full request that the caller must forward or release.
     */
    fun rebuild(original: FullHttpRequest, edit: BreakpointRequestEdit): FullHttpRequest {
        val content = when (val bodyEdit = edit.body) {
            BreakpointBodyEdit.Unchanged -> original.content().retainedDuplicate()
            is BreakpointBodyEdit.Replace -> Unpooled.copiedBuffer(bodyEdit.body.copyBytes())
        }

        val rebuilt = DefaultFullHttpRequest(
            original.protocolVersion(),
            HttpMethod.valueOf(edit.request.head.method.token),
            relativeTarget(edit.request.head.target),
            content,
        )
        val preserveTrailers = edit.body == BreakpointBodyEdit.Unchanged &&
            !original.trailingHeaders().isEmpty

        rebuilt.headers().replaceWithFullMessageHeaders(
            headers = edit.request.head.headers,
            contentLength = content.readableBytes().toLong(),
            trailerNames = if (preserveTrailers) original.trailingHeaders().names() else emptySet(),
        )
        if (preserveTrailers) {
            rebuilt.trailingHeaders().set(original.trailingHeaders())
        }
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
