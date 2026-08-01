package com.devuloopers.knet.engine.proxy.stress

import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.DefaultHttpHeaders
import org.junit.Assert.assertEquals
import org.junit.Test

class KeepAliveTest {

    @Test
    fun testKeepAliveConnectionHeader() {
        val headers: HttpHeaders = DefaultHttpHeaders()
        headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)

        assertEquals("keep-alive", headers.get(HttpHeaderNames.CONNECTION))
    }
}
