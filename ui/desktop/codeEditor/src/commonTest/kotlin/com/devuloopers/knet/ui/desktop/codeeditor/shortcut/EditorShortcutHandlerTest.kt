package com.devuloopers.knet.ui.desktop.codeeditor.shortcut

import androidx.compose.ui.input.key.Key
import com.devuloopers.knet.ui.desktop.codeeditor.model.EditorCaretState
import com.devuloopers.knet.ui.desktop.codeeditor.model.EditorSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorShortcutHandlerTest {

    @Test
    fun testSelectAllShortcut() {
        val lines = listOf("line 1", "line 2", "line 3")
        var capturedSelection: EditorSelection? = null

        val handled = EditorShortcutHandler.processKey(
            key = Key.A,
            isCmdOrCtrl = true,
            rawLines = lines,
            selection = null,
            caretState = null,
            copyAction = {},
            pasteAction = { null },
            onDocumentLinesChanged = null,
            onSelectionChange = { capturedSelection = it },
            onCaretStateChange = null
        )

        assertTrue(handled)
        assertNotNull(capturedSelection)
        assertEquals(0, capturedSelection!!.startLine)
        assertEquals(0, capturedSelection!!.startCol)
        assertEquals(2, capturedSelection!!.endLine)
        assertEquals(6, capturedSelection!!.endCol)
    }

    @Test
    fun testBackspaceDeletesActiveSelection() {
        val lines = listOf("hello world", "second line", "third line")
        var updatedLines: List<String>? = null
        var updatedSelection: EditorSelection? = EditorSelection(0, 5, 1, 6)
        var updatedCaret: EditorCaretState? = null

        val handled = EditorShortcutHandler.processKey(
            key = Key.Backspace,
            isCmdOrCtrl = false,
            rawLines = lines,
            selection = updatedSelection,
            caretState = null,
            copyAction = {},
            pasteAction = { null },
            onDocumentLinesChanged = { updatedLines = it },
            onSelectionChange = { updatedSelection = it },
            onCaretStateChange = { updatedCaret = it }
        )

        assertTrue(handled)
        assertNull(updatedSelection)
        val nonNullLines = updatedLines
        assertNotNull(nonNullLines)
        assertEquals(2, nonNullLines.size)
        assertEquals("hello line", nonNullLines[0])
        assertEquals("third line", nonNullLines[1])
        val nonNullCaret = updatedCaret
        assertNotNull(nonNullCaret)
        assertEquals(0, nonNullCaret.lineIndex)
        assertEquals(5, nonNullCaret.colIndex)
    }

    @Test
    fun testWordSelectionBackspaceDeletion() {
        val lines = listOf("hello world", "second line")
        var updatedLines: List<String>? = null
        var updatedSelection: EditorSelection? = EditorSelection(0, 6, 0, 11)
        var updatedCaret: EditorCaretState? = null

        val handled = EditorShortcutHandler.processKey(
            key = Key.Backspace,
            isCmdOrCtrl = false,
            rawLines = lines,
            selection = updatedSelection,
            caretState = null,
            copyAction = {},
            pasteAction = { null },
            onDocumentLinesChanged = { updatedLines = it },
            onSelectionChange = { updatedSelection = it },
            onCaretStateChange = { updatedCaret = it }
        )

        assertTrue(handled)
        assertNull(updatedSelection)
        assertNotNull(updatedLines)
        assertEquals("hello ", updatedLines[0])
        assertNotNull(updatedCaret)
        assertEquals(0, updatedCaret.lineIndex)
        assertEquals(6, updatedCaret.colIndex)
    }

}



