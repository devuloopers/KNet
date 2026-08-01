package com.devuloopers.knet.core.logger

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class LoggerThreadSafetyTest {

    @Test
    fun testConcurrentLoggingSafety() = runTest {
        val jobs = (1..50).map { id ->
            async(Dispatchers.Default) {
                KNetLogger.info(LogTags.PROXY) { "Concurrent log $id" }
                KNetLogger.debug(LogTags.HTTP) { "Concurrent debug $id" }
                LoggerFactory.get("Worker-$id").i { "Worker thread log $id" }
            }
        }
        jobs.awaitAll()
        assertTrue(true)
    }
}
