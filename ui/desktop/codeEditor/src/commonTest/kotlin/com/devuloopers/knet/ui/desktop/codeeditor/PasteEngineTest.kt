package com.devuloopers.knet.ui.desktop.codeeditor

import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.DocumentBuffer
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.PasteEngine
import com.devuloopers.knet.ui.desktop.codeeditor.model.EditorCaretState
import kotlin.test.Test
import kotlin.test.assertEquals

class PasteEngineTest {

    @Test
    fun testSingleLinePaste() {
        val buffer = DocumentBuffer(listOf("hello world"))
        val caretState = PasteEngine.applyPaste(
            buffer = buffer,
            lineIndex = 0,
            caretCol = 5,
            pastedText = " BEAUTIFUL"
        )
        assertEquals(listOf("hello BEAUTIFUL world"), buffer.getLines())
        assertEquals(EditorCaretState(lineIndex = 0, colIndex = 15), caretState)
    }

    @Test
    fun testMultiLinePaste() {
        val buffer = DocumentBuffer(listOf("start end"))
        val caretState = PasteEngine.applyPaste(
            buffer = buffer,
            lineIndex = 0,
            caretCol = 5,
            pastedText = "_line1\n_line2\n_line3"
        )
        val expectedLines = listOf(
            "start_line1",
            "_line2",
            "_line3 end"
        )
        assertEquals(expectedLines, buffer.getLines())
        assertEquals(EditorCaretState(lineIndex = 2, colIndex = 6), caretState)
    }

    @Test
    fun testMultiLinePasteWithWindowsCRLF() {
        val buffer = DocumentBuffer(listOf("{\"key\": }"))
        val caretState = PasteEngine.applyPaste(
            buffer = buffer,
            lineIndex = 0,
            caretCol = 8,
            pastedText = "\"value1\",\r\n\"value2\""
        )
        val expectedLines = listOf(
            "{\"key\": \"value1\",",
            "\"value2\"}"
        )
        assertEquals(expectedLines, buffer.getLines())
        assertEquals(EditorCaretState(lineIndex = 1, colIndex = 8), caretState)
    }
}
