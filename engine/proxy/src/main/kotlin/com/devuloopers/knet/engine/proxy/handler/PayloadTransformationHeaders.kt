package com.devuloopers.knet.engine.proxy.handler

import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaders

/** Removes framing and integrity metadata that becomes stale when a transformer can edit bytes. */
internal object PayloadTransformationHeaders {
    fun sanitizeRequest(headers: HttpHeaders) {
        headers.remove(HttpHeaderNames.CONTENT_LENGTH)
        removeIntegrityMetadata(headers)
    }

    fun sanitizeResponse(headers: HttpHeaders) {
        headers.remove(HttpHeaderNames.CONTENT_LENGTH)
        headers.remove(HttpHeaderNames.ETAG)
        headers.remove(HttpHeaderNames.CONTENT_RANGE)
        removeIntegrityMetadata(headers)
    }

    private fun removeIntegrityMetadata(headers: HttpHeaders) {
        headers.remove(CONTENT_MD5)
        headers.remove(DIGEST)
        headers.remove(CONTENT_DIGEST)
        headers.remove(REPR_DIGEST)
    }

    private const val CONTENT_MD5: String = "content-md5"
    private const val DIGEST: String = "digest"
    private const val CONTENT_DIGEST: String = "content-digest"
    private const val REPR_DIGEST: String = "repr-digest"
}
