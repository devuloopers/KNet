package com.devuloopers.knet.ui.desktop.codeeditor

import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.DocumentBuffer
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.SelectionEngine
import com.devuloopers.knet.ui.desktop.codeeditor.model.EditorCaretState
import com.devuloopers.knet.ui.desktop.codeeditor.model.EditorSelection
import kotlin.test.Test
import kotlin.test.assertEquals

class SelectionEngineTest {

    @Test
    fun testExtractSingleLineText() {
        val buffer = DocumentBuffer(listOf("hello world"))
        val selection = EditorSelection(startLine = 0, startCol = 0, endLine = 0, endCol = 5)
        val extracted = SelectionEngine.extractSelectedText(buffer, selection)
        assertEquals("hello", extracted)
    }

    @Test
    fun testExtractMultiLineText() {
        val buffer = DocumentBuffer(listOf("line0 text", "line1 text", "line2 text"))
        val selection = EditorSelection(startLine = 0, startCol = 6, endLine = 2, endCol = 5)
        val extracted = SelectionEngine.extractSelectedText(buffer, selection)
        assertEquals("text\nline1 text\nline2", extracted)
    }

    @Test
    fun testDeleteMultiLineText() {
        val buffer = DocumentBuffer(listOf("line0 text", "line1 text", "line2 text"))
        val selection = EditorSelection(startLine = 0, startCol = 5, endLine = 2, endCol = 5)
        val caretState = SelectionEngine.deleteSelectedText(buffer, selection)

        assertEquals(listOf("line0 text"), buffer.getLines())
        assertEquals(EditorCaretState(0, 5), caretState)
    }

    @Test
    fun testComputeLineBoundsMultiLine() {
        val selection = EditorSelection(startLine = 0, startCol = 6, endLine = 2, endCol = 5)

        // Line 0 (start line)
        val line0Bounds = SelectionEngine.computeLineBounds(selection, lineIndex = 0, lineLength = 10)
        assertEquals(com.devuloopers.knet.ui.desktop.codeeditor.model.LineSelectionBounds(6, 10, isStartLine = true, isEndLine = false, isMiddleLine = false), line0Bounds)

        // Line 1 (middle line)
        val line1Bounds = SelectionEngine.computeLineBounds(selection, lineIndex = 1, lineLength = 10)
        assertEquals(com.devuloopers.knet.ui.desktop.codeeditor.model.LineSelectionBounds(0, 10, isStartLine = false, isEndLine = false, isMiddleLine = true), line1Bounds)

        // Line 2 (end line — clips at endCol = 5)
        val line2Bounds = SelectionEngine.computeLineBounds(selection, lineIndex = 2, lineLength = 10)
        assertEquals(com.devuloopers.knet.ui.desktop.codeeditor.model.LineSelectionBounds(0, 5, isStartLine = false, isEndLine = true, isMiddleLine = false), line2Bounds)
    }
}


