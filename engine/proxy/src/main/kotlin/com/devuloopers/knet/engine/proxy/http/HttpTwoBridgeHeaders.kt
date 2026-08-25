package com.devuloopers.knet.engine.proxy.http

import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http2.HttpConversionUtil

/**
 * Contains Netty's transport-private HTTP/2 object-bridge extension headers.
 *
 * Netty represents an HTTP/2 stream with HTTP/1-shaped objects inside the proxy pipeline. It uses
 * extension headers to retain pseudo-header and stream metadata during that conversion. These
 * fields are not application headers and must not enter KNet's canonical models or an HTTP/1 wire.
 */
internal object HttpTwoBridgeHeaders {
    private val names = HttpConversionUtil.ExtensionHeaderNames.values()

    /** Returns whether [name] belongs to Netty's private HTTP/2 object bridge. */
    fun contains(name: CharSequence): Boolean = names.any { extension ->
        extension.text().toString().equals(name.toString(), ignoreCase = true)
    }

    /** Removes every Netty HTTP/2 bridge field from [headers]. */
    fun removeFrom(headers: HttpHeaders) {
        names.forEach { extension -> headers.remove(extension.text()) }
    }

    /**
     * Prepares [headers] for Netty's outbound HTTP/2 codec with exactly one internal scheme field.
     *
     * Existing bridge metadata is discarded first so downstream stream identity cannot be reused
     * accidentally by a different upstream HTTP/2 stream.
     */
    fun prepareForHttpTwo(headers: HttpHeaders, scheme: String) {
        removeFrom(headers)
        headers.set(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), scheme)
    }
}
