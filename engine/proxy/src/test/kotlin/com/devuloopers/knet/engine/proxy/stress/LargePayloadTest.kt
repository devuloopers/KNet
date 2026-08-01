package com.devuloopers.knet.engine.proxy.stress

import io.netty.buffer.Unpooled
import org.junit.Assert.assertEquals
import org.junit.Test

class LargePayloadTest {

    @Test
    fun testLargePayloadBufferHandling() {
        val largeBytes = ByteArray(10 * 1024 * 1024) // 10MB
        val buffer = Unpooled.wrappedBuffer(largeBytes)

        assertEquals(10485760, buffer.readableBytes())
        buffer.release()
    }
}
