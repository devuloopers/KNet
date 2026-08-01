package com.devuloopers.knet.engine.interceptor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ChannelAttributesTest {

    @Test
    fun testChannelAttributesKeysExist() {
        assertNotNull(ChannelAttributes.REQUEST_ATTR)
        assertNotNull(ChannelAttributes.HOST_ATTR)
        assertNotNull(ChannelAttributes.SSL_ATTR)

        assertEquals("knet.request", ChannelAttributes.REQUEST_ATTR.name())
        assertEquals("knet.host", ChannelAttributes.HOST_ATTR.name())
        assertEquals("knet.ssl", ChannelAttributes.SSL_ATTR.name())
    }
}
