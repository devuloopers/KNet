package com.devuloopers.knet.engine.traffic

import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertTrue

class PerformanceRegressionTest {

    @Test
    fun testRegexCachePerformanceVsRecompilation() {
        val pattern = ".*\\.example\\.com/api/v1/users/[0-9]+/details.*"

        val unCachedTime = measureTimeMillis {
            repeat(1000) {
                Regex(pattern).containsMatchIn("https://sub.example.com/api/v1/users/42/details")
            }
        }

        val cachedTime = measureTimeMillis {
            repeat(1000) {
                RegexCache.getOrNull(pattern)?.containsMatchIn("https://sub.example.com/api/v1/users/42/details")
            }
        }

        assertTrue(cachedTime < unCachedTime, "Cached Regex lookups must be significantly faster than 1000 un-cached Regex compilations")
    }
}
