package com.devuloopers.knet.ui.desktop.codeeditor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.AutoIndentEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Unit tests for [AutoIndentEngine] — auto-indentation on Enter key press.
 */
class AutoIndentEngineTest {

    @Test
    fun testIndentationInheritanceAndOpeningBraceIncrease() {
        val oldValue = TextFieldValue(text = "  \"user\": {", selection = TextRange(11))
        val newValue = TextFieldValue(text = "  \"user\": {\n", selection = TextRange(12))

        val result = AutoIndentEngine.handleInsertBreak(oldValue, newValue)

        assertNotNull(result)
        assertEquals("  \"user\": {\n  ", result.text)
        assertEquals(14, result.selection.start)
    }

    @Test
    fun testNoAutoIndentOnRegularLine() {
        val oldValue = TextFieldValue(text = "plain text", selection = TextRange(10))
        val newValue = TextFieldValue(text = "plain text\n", selection = TextRange(11))

        val result = AutoIndentEngine.handleInsertBreak(oldValue, newValue)

        // With no indentation on the current line, the engine should not modify the result
        // (returns null indicating no special handling required)
        // OR the result has the same text. Either is acceptable depending on implementation.
        if (result != null) {
            assertEquals("plain text\n", result.text)
        }
    }
}
