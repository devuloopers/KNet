package com.devuloopers.knet.core.logger

import kotlin.test.Test
import kotlin.test.assertTrue

class KNetLoggerTest {

    @Test
    fun testKNetLoggerLevelsExecuteWithoutException() {
        KNetLogger.verbose(LogTags.KNET) { "Verbose test message" }
        KNetLogger.debug(LogTags.PROXY) { "Debug test message" }
        KNetLogger.info(LogTags.HTTP) { "Info test message" }
        KNetLogger.warn(LogTags.TRAFFIC) { "Warn test message" }
        KNetLogger.error(LogTags.SESSION) { "Error test message" }
        assertTrue(true)
    }

    @Test
    fun testDefaultTagsInKNetLogger() {
        KNetLogger.info { "Default tag message" }
        KNetLogger.debug { "Default tag debug message" }
        assertTrue(true)
    }
}
