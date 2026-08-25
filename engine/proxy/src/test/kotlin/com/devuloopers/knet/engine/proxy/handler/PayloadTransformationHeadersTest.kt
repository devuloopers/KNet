package com.devuloopers.knet.engine.proxy.handler

import io.netty.handler.codec.http.DefaultHttpHeaders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PayloadTransformationHeadersTest {
    @Test
    fun `response transformation removes stale framing validators and digests`() {
        val headers = DefaultHttpHeaders().apply {
            set("content-length", "12")
            set("etag", "strong")
            set("content-range", "bytes 0-11/12")
            set("content-md5", "md5")
            set("digest", "sha-256=value")
            set("content-digest", "sha-256=:value:")
            set("repr-digest", "sha-256=:value:")
            set("content-encoding", "gzip")
        }

        PayloadTransformationHeaders.sanitizeResponse(headers)

        listOf(
            "content-length",
            "etag",
            "content-range",
            "content-md5",
            "digest",
            "content-digest",
            "repr-digest",
        ).forEach { name -> assertFalse(headers.contains(name), name) }
        assertEquals("gzip", headers["content-encoding"])
    }

    @Test
    fun `request transformation preserves negotiation headers`() {
        val headers = DefaultHttpHeaders().apply {
            set("content-length", "12")
            set("content-md5", "md5")
            set("digest", "sha-256=value")
            set("want-digest", "sha-256")
        }

        PayloadTransformationHeaders.sanitizeRequest(headers)

        assertFalse(headers.contains("content-length"))
        assertFalse(headers.contains("content-md5"))
        assertFalse(headers.contains("digest"))
        assertEquals("sha-256", headers["want-digest"])
    }
}
