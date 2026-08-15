package com.devuloopers.knet.engine.traffic

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RegexCacheTest {

    @Test
    fun testRegexCachingAndReuse() {
        RegexCache.clear()
        val pattern = ".*\\.example\\.com/api.*"

        val regex1 = RegexCache.getOrNull(pattern)
        assertNotNull(regex1)
        assertTrue(regex1.containsMatchIn("https://sub.example.com/api/users"))

        val regex2 = RegexCache.getOrNull(pattern)
        assertSame(regex1, regex2, "Consecutive lookups must return identical compiled Regex instance")
    }

    @Test
    fun testInvalidPatternRejection() {
        assertNull(RegexCache.getOrNull(""))
        assertNull(RegexCache.getOrNull("[unclosed-group"))
    }

    @Test
    fun testCapacityAndLruEviction() {
        RegexCache.clear()
        // Insert 1000 items
        for (i in 1..1000) {
            RegexCache.getOrNull("pattern_$i")
        }

        // Access pattern_1 to make it most-recently used
        val pattern1 = RegexCache.getOrNull("pattern_1")
        assertNotNull(pattern1)

        // Insert 1001st pattern - should evict least recently used (pattern_2)
        RegexCache.getOrNull("pattern_1001")

        // pattern_1 was recently accessed so it should still be cached
        val pattern1Again = RegexCache.getOrNull("pattern_1")
        assertSame(pattern1, pattern1Again, "Hot pattern must be retained by LRU cache")
    }
}
