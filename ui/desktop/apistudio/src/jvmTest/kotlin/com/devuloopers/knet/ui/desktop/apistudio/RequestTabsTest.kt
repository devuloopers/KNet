package com.devuloopers.knet.ui.desktop.apistudio

import com.devuloopers.knet.ui.desktop.apistudio.model.RequestTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Unit tests for RequestTab data class in `:ui:desktop:apistudio`.
 */
class RequestTabsTest {

    @Test
    fun `RequestTab holds properties correctly`() {
        val tab = RequestTab(id = "tab_req_1", title = "Get Users", method = "POST")
        assertEquals("tab_req_1", tab.id)
        assertEquals("Get Users", tab.title)
        assertEquals("POST", tab.method)
        assertFalse(tab.isDirty)
    }
}
