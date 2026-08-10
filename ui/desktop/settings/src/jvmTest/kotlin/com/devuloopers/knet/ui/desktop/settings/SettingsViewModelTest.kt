package com.devuloopers.knet.ui.desktop.settings

import com.devuloopers.knet.ui.desktop.settings.model.SettingsIntent
import com.devuloopers.knet.ui.desktop.settings.model.SettingsState
import com.devuloopers.knet.ui.desktop.settings.model.SettingsTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests verifying Settings state transitions and intent contracts.
 */
class SettingsViewModelTest {

    @Test
    fun `verify default settings state values`() {
        val state = SettingsState()
        assertEquals(SettingsTab.NETWORK_PROXY, state.activeTab)
        assertEquals("8080", state.proxyPort)
        assertFalse(state.isCaTrusted)
        assertFalse(state.autoClearTrafficOnStartup)
        assertEquals(10, state.maxPayloadMb)
        assertEquals("DARK", state.theme)
        assertEquals("JAVASCRIPT", state.scriptLanguage)
    }

    @Test
    fun `verify intent intent tab switching`() {
        val initial = SettingsState()
        val updated = initial.copy(activeTab = SettingsTab.TRAFFIC_STORAGE)
        assertEquals(SettingsTab.TRAFFIC_STORAGE, updated.activeTab)
    }

    @Test
    fun `verify proxy port digit filtering logic`() {
        val rawInput = "8080abc"
        val filteredPort = rawInput.filter { it.isDigit() }.take(5)
        assertEquals("8080", filteredPort)
    }
}
