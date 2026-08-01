package com.devuloopers.knet.core.logger

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LoggerFactoryTest {

    @Test
    fun testLoggerFactoryGetLoggerByTag() {
        val proxyLogger = LoggerFactory.get(LogTags.PROXY)
        assertNotNull(proxyLogger)
        assertEquals(LogTags.PROXY, proxyLogger.tag)

        val httpLogger = LoggerFactory.get(LogTags.HTTP)
        assertNotNull(httpLogger)
        assertEquals(LogTags.HTTP, httpLogger.tag)
    }

    @Test
    fun testLoggerFactoryDefaultTag() {
        val defaultLogger = LoggerFactory.get()
        assertEquals(LogTags.KNET, defaultLogger.tag)
    }
}
