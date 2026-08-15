package com.devuloopers.knet.engine.traffic

import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertTrue

class PerformanceRegressionTest {

    @Test
    fun testRegexCachePerformanceVsRecompilation() {
        val pattern = ".*\\.example\\.com/api/v1/users/[0-9]+/details.*"
        val testUrl = "https://sub.example.com/api/v1/users/42/details"

        // Warm up JIT
        repeat(100) {
            Regex(pattern).containsMatchIn(testUrl)
            RegexCache.getOrNull(pattern)?.containsMatchIn(testUrl)
        }

        val unCachedTime = measureTimeMillis {
            repeat(5000) {
                Regex(pattern).containsMatchIn(testUrl)
            }
        }

        val cachedTime = measureTimeMillis {
            repeat(5000) {
                RegexCache.getOrNull(pattern)?.containsMatchIn(testUrl)
            }
        }

        assertTrue(cachedTime <= unCachedTime, "Cached Regex lookups must be faster or equal to un-cached Regex compilations (cached=$cachedTime ms, uncached=$unCachedTime ms)")
    }

}
