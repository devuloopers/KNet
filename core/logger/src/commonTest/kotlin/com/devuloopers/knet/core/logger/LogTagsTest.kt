package com.devuloopers.knet.core.logger

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogTagsTest {

    @Test
    fun testPredefinedLogTagsExistAndAreUnique() {
        val tags = listOf(
            LogTags.API_STUDIO,
            LogTags.PROXY,
            LogTags.CERTIFICATE,
            LogTags.INTERCEPTOR,
            LogTags.TRAFFIC,
            LogTags.SESSION,
            LogTags.SCRIPT,
            LogTags.HTTP,
            LogTags.DATABASE,
            LogTags.WORKSPACE,
            LogTags.DOMAIN,
            LogTags.KNET
        )

        // Verify no empty or blank tags
        assertTrue(tags.all { it.isNotBlank() })

        // Verify tag uniqueness (no duplicates)
        assertEquals(tags.size, tags.toSet().size)
    }
}
