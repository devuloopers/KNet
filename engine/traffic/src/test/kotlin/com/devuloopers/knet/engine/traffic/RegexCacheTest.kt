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
}
