package com.devuloopers.knet.ui.desktop.codeeditor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.AutoIndentEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AutoIndentEngineTest {

    @Test
    fun testIndentationInheritanceAndOpeningBraceIncrease() {
        val oldValue = TextFieldValue(text = "  \"user\": {", selection = TextRange(11))
        val newValue = TextFieldValue(text = "  \"user\": {\n", selection = TextRange(12))

        val result = AutoIndentEngine.handleInsertBreak(oldValue, newValue, tabSize = 2)

        assertNotNull(result)
        assertEquals("  \"user\": {\n    ", result.text)
        assertEquals(16, result.selection.start)
    }

    @Test
    fun testBracketPairExpansion() {
        val oldValue = TextFieldValue(text = "  \"data\": {}", selection = TextRange(11))
        val newValue = TextFieldValue(text = "  \"data\": {\n}", selection = TextRange(12))

        val result = AutoIndentEngine.handleInsertBreak(oldValue, newValue, tabSize = 2)

        assertNotNull(result)
        assertEquals("  \"data\": {\n    \n  }", result.text)
        assertEquals(16, result.selection.start)
    }
}
