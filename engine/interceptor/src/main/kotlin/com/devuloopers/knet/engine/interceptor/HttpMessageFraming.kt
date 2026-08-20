package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.traffic.model.http.HeaderField
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpHeaders

/**
 * Applies canonical edited headers using one deterministic full-message framing policy.
 *
 * Aggregated messages normally use Content-Length. A message with retained trailing fields uses
 * chunked framing and derives its Trailer declaration from those fields. Caller-provided framing
 * headers are ignored so Content-Length and Transfer-Encoding can never conflict.
 */
internal fun HttpHeaders.replaceWithFullMessageHeaders(
    headers: List<HeaderField>,
    contentLength: Long?,
    trailerNames: Set<String> = emptySet(),
) {
    clear()
    headers.forEach { header ->
        if (!header.name.value.isFramingHeader()) {
            add(header.name.value, header.value)
        }
    }
    if (trailerNames.isNotEmpty()) {
        set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
        set(HttpHeaderNames.TRAILER, trailerNames.joinToString(", "))
    } else {
        contentLength?.let { set(HttpHeaderNames.CONTENT_LENGTH, it) }
    }
}

private fun String.isFramingHeader(): Boolean =
    equals(HttpHeaderNames.CONTENT_LENGTH.toString(), ignoreCase = true) ||
        equals(HttpHeaderNames.TRANSFER_ENCODING.toString(), ignoreCase = true) ||
        equals(HttpHeaderNames.TRAILER.toString(), ignoreCase = true)
