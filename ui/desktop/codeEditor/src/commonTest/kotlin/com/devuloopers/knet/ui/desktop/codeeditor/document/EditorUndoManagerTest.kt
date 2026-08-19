package com.devuloopers.knet.ui.desktop.codeeditor.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorUndoManagerTest {
    @Test
    fun adjacentInsertionsUndoAsOneDeltaGroup() {
        val document = ChunkedEditorDocument()
        val history = EditorUndoManager()

        history.record(
            document.apply(
                EditorTextEdit(EditorRange.caret(EditorPosition(0, 0)), "a", EditorEditKind.Insertion)
            )
        )
        history.record(
            document.apply(
                EditorTextEdit(EditorRange.caret(EditorPosition(0, 1)), "b", EditorEditKind.Insertion)
            )
        )

        assertEquals("ab", document.snapshot.text())
        assertTrue(history.canUndo)
        history.undo(document)
        assertEquals("", document.snapshot.text())
        assertTrue(history.canRedo)
        assertFalse(history.canUndo)
        history.redo(document)
        assertEquals("ab", document.snapshot.text())
    }

    @Test
    fun structuralChangesUseSeparateGroups() {
        val document = ChunkedEditorDocument("a")
        val history = EditorUndoManager()
        history.record(
            document.apply(
                EditorTextEdit(EditorRange.caret(EditorPosition(0, 1)), "b", EditorEditKind.Insertion)
            )
        )
        history.record(
            document.apply(
                EditorTextEdit(EditorRange.caret(EditorPosition(0, 2)), "\n", EditorEditKind.Structural)
            )
        )

        history.undo(document)
        assertEquals("ab", document.snapshot.text())
        history.undo(document)
        assertEquals("a", document.snapshot.text())
    }
}
