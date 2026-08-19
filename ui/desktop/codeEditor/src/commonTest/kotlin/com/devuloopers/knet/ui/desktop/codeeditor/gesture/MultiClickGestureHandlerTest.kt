package com.devuloopers.knet.ui.desktop.codeeditor.gesture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MultiClickGestureHandlerTest {

    @Test
    fun testProcessClickSingleClickReturnsNull() {
        val handler = MultiClickGestureHandler()
        val selection = handler.processClick(1, 5, "    \"data\": {", currentTimeMs = 1000L)
        assertNull(selection)
        assertEquals(1, handler.clickCount)
    }

    @Test
    fun testProcessClickDoubleClickReturnsWordSelection() {
        val handler = MultiClickGestureHandler()
        val lineText = "    \"data\": {"

        // 1st click
        handler.processClick(1, 6, lineText, currentTimeMs = 1000L)

        // 2nd click within 300ms
        val selection = handler.processClick(1, 6, lineText, currentTimeMs = 1100L)
        assertNotNull(selection)
        assertEquals(2, handler.clickCount)
        assertEquals(1, selection.anchor.line)
        assertEquals(5, selection.anchor.column)
        assertEquals(1, selection.active.line)
        assertEquals(9, selection.active.column)
    }

    @Test
    fun testProcessClickTripleClickReturnsLineSelection() {
        val handler = MultiClickGestureHandler()
        val lineText = "    \"data\": {"

        handler.processClick(1, 6, lineText, currentTimeMs = 1000L)
        handler.processClick(1, 6, lineText, currentTimeMs = 1100L)
        val selection = handler.processClick(1, 6, lineText, currentTimeMs = 1200L)

        assertNotNull(selection)
        assertEquals(3, handler.clickCount)
        assertEquals(1, selection.anchor.line)
        assertEquals(0, selection.anchor.column)
        assertEquals(1, selection.active.line)
        assertEquals(lineText.length, selection.active.column)
    }

}
