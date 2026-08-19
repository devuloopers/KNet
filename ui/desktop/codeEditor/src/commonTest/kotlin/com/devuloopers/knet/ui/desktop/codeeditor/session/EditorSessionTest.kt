package com.devuloopers.knet.ui.desktop.codeeditor.session

import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorPosition
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorRange
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorSessionTest {
    @Test
    fun lineReplacementPublishesMinimalEdit() {
        val session = EditorSession("alpha")
        val events = mutableListOf<EditorSessionEvent>()
        session.subscribe { events += it }

        val change = session.replaceLine(0, "alXha")

        assertEquals(EditorRange(EditorPosition(0, 2), EditorPosition(0, 3)), change?.beforeRange)
        assertEquals("p", change?.removedText)
        assertEquals("X", change?.insertedText)
        assertEquals("alXha", session.snapshot.text())
        assertEquals(1, events.size)
    }

    @Test
    fun splitMergeUndoAndRedoRemainSessionOwned() {
        val session = EditorSession("value")
        val events = mutableListOf<EditorSessionEvent>()
        session.subscribe { events += it }

        session.splitLine(EditorPosition(0, 2), indentation = "  ")
        assertEquals("va\n  lue", session.snapshot.text())
        assertEquals(EditorPosition(1, 2), session.caret)

        assertTrue(session.undo())
        assertEquals("value", session.snapshot.text())
        assertTrue(events.last().documentChanges.isNotEmpty())
        assertEquals(EditorChangeOrigin.Undo, events.last().origin)
        assertTrue(session.redo())
        assertEquals("va\n  lue", session.snapshot.text())
        assertTrue(events.last().documentChanges.isNotEmpty())
        assertEquals(EditorChangeOrigin.Redo, events.last().origin)
    }

    @Test
    fun externalReplacementClearsHistory() {
        val session = EditorSession("a")
        session.replaceLine(0, "ab")
        assertTrue(session.canUndo)

        session.replaceAllFromExternal("external")

        assertEquals("external", session.snapshot.text())
        assertFalse(session.canUndo)
    }

    @Test
    fun selectionRetainsDirectionWhileExposingNormalizedRange() {
        val session = EditorSession("alpha\nbeta")
        val selection = EditorSelection(
            anchor = EditorPosition(1, 3),
            active = EditorPosition(0, 1)
        )

        session.select(selection)

        assertEquals(selection, session.selection)
        assertEquals(EditorPosition(0, 1), session.selection?.range?.start)
        assertEquals(EditorPosition(1, 3), session.selection?.range?.end)
        assertEquals(EditorPosition(0, 1), session.caret)
    }
}
