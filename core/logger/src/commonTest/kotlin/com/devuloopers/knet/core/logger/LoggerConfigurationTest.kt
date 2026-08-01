package com.devuloopers.knet.core.logger

import co.touchlab.kermit.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoggerConfigurationTest {

    @Test
    fun testLoggerConfigurationDefaults() {
        val config = LoggerConfiguration()
        assertEquals(Severity.Info, config.minimumSeverity)
        assertFalse(config.enableThreadName)
        assertTrue(config.enableTimestamp)
    }

    @Test
    fun testLoggerFactoryConfigurationUpdate() {
        val customConfig = LoggerConfiguration(
            minimumSeverity = Severity.Debug,
            enableThreadName = true,
            enableTimestamp = false
        )
        LoggerFactory.configure(customConfig)
        val logger = LoggerFactory.get(LogTags.PROXY)
        assertEquals(LogTags.PROXY, logger.tag)
    }
}
