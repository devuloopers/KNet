package com.devuloopers.knet.engine.proxy.stress

import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.DefaultHttpHeaders
import org.junit.Assert.assertEquals
import org.junit.Test

class ChunkedTransferTest {

    @Test
    fun testChunkedTransferEncodingHeader() {
        val headers: HttpHeaders = DefaultHttpHeaders()
        headers.set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)

        assertEquals("chunked", headers.get(HttpHeaderNames.TRANSFER_ENCODING))
    }
}
