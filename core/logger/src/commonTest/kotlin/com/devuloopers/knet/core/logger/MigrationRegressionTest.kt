package com.devuloopers.knet.core.logger

import kotlin.test.Test
import kotlin.test.assertEquals

class MigrationRegressionTest {

    @Test
    fun testLogTagsConstantsIntegrity() {
        assertEquals("ApiStudio", LogTags.API_STUDIO)
        assertEquals("Proxy", LogTags.PROXY)
        assertEquals("Certificate", LogTags.CERTIFICATE)
        assertEquals("Interceptor", LogTags.INTERCEPTOR)
        assertEquals("Traffic", LogTags.TRAFFIC)
        assertEquals("Session", LogTags.SESSION)
        assertEquals("Script", LogTags.SCRIPT)
        assertEquals("Http", LogTags.HTTP)
        assertEquals("Database", LogTags.DATABASE)
        assertEquals("Workspace", LogTags.WORKSPACE)
        assertEquals("Domain", LogTags.DOMAIN)
        assertEquals("KNet", LogTags.KNET)
    }
}
