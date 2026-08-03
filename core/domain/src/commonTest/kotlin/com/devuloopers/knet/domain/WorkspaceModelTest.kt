package com.devuloopers.knet.domain

import com.devuloopers.knet.domain.workspace.model.EnvironmentStore
import com.devuloopers.knet.domain.workspace.model.WorkspaceLayoutSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkspaceModelTest {

    @Test
    fun testEnvironmentStoreSetGetAndClear() {
        val store = EnvironmentStore()
        assertNull(store.get("baseUrl"))

        store.set("baseUrl", "https://api.dev.knet")
        assertEquals("https://api.dev.knet", store.get("baseUrl"))

        store.set("apiKey", "secret-123")
        assertEquals("secret-123", store.get("apiKey"))

        store.clear()
        assertNull(store.get("baseUrl"))
        assertNull(store.get("apiKey"))
        assertEquals(0, store.variables.size)
    }

    @Test
    fun testEnvironmentStoreInitializationWithMap() {
        val initialMap = mutableMapOf("env" to "staging", "version" to "1.0.0")
        val store = EnvironmentStore(initialMap)

        assertEquals("staging", store.get("env"))
        assertEquals("1.0.0", store.get("version"))
    }

    @Test
    fun testWorkspaceLayoutSettingsDefaultsAndCopy() {
        val defaultSettings = WorkspaceLayoutSettings()

        assertTrue(defaultSettings.isTrafficFeedVisible)
        assertTrue(defaultSettings.isInspectorVisible)
        assertEquals(600f, defaultSettings.trafficFeedWidthDp)
        assertEquals(260f, defaultSettings.sidebarWidthDp)
        assertEquals(180f, defaultSettings.bottomTrayHeightDp)

        val updatedSettings = WorkspaceLayoutSettings(
            isTrafficFeedVisible = false,
            trafficFeedWidthDp = 500f,
            sidebarWidthDp = 300f
        )

        assertEquals(false, updatedSettings.isTrafficFeedVisible)
        assertEquals(500f, updatedSettings.trafficFeedWidthDp)
        assertEquals(300f, updatedSettings.sidebarWidthDp)
    }
}
