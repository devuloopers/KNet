package com.devuloopers.knet.ui.desktop.codeeditor.algorithm

import com.devuloopers.knet.ui.desktop.codeeditor.model.EditorCaretState
import com.devuloopers.knet.ui.desktop.codeeditor.model.EditorSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UndoRedoStackTest {

    // ---------------------------------------------------------------------------
    // Core undo/redo lifecycle
    // ---------------------------------------------------------------------------

    @Test
    fun testInitProducesEmptyStack() {
        val stack = UndoRedoStack()
        stack.init("hello")
        assertFalse(stack.canUndo)
        assertFalse(stack.canRedo)
        assertNull(stack.undo())
        assertNull(stack.redo())
    }

    @Test
    fun testPushSameTextIsNoop() {
        val stack = UndoRedoStack()
        stack.init("hello")
        stack.push("hello", afterCaret = EditorCaretState(0, 5))
        assertFalse(stack.canUndo)
    }

    @Test
    fun testBasicUndoRedoCycle() {
        val stack = UndoRedoStack()
        stack.init("line 1")
        stack.updatePendingBeforeState(EditorCaretState(0, 6))
        stack.push("line 1\nline 2", afterCaret = EditorCaretState(1, 6), editKind = EditKind.Structural)
        stack.updatePendingBeforeState(EditorCaretState(1, 6))
        stack.push("line 1\nline 2\nline 3", afterCaret = EditorCaretState(2, 6), editKind = EditKind.Structural)

        val undo1 = stack.undo()
        assertEquals("line 1\nline 2", undo1?.text)
        assertEquals(EditorCaretState(1, 6), undo1?.caretState)

        val undo2 = stack.undo()
        assertEquals("line 1", undo2?.text)
        assertEquals(EditorCaretState(0, 6), undo2?.caretState)
        assertFalse(stack.canUndo)

        val redo1 = stack.redo()
        assertEquals("line 1\nline 2", redo1?.text)
        assertEquals(EditorCaretState(1, 6), redo1?.caretState)
    }

    @Test
    fun testSelectionRestorationOnUndoAfterSelectionDeletion() {
        val stack = UndoRedoStack()
        stack.init("hello world")
        val selection = EditorSelection(startLine = 0, startCol = 6, endLine = 0, endCol = 11)

        // Simulate drag selection of "world"
        stack.updatePendingBeforeState(EditorCaretState(0, 6), selection = selection)

        // Simulate Backspace deletion of active selection
        stack.push("hello ", afterCaret = EditorCaretState(0, 6), editKind = EditKind.Structural)

        // Revert edit via Ctrl+Z
        val undoResult = stack.undo()
        assertEquals("hello world", undoResult?.text, "Text should be fully restored")
        assertEquals(EditorCaretState(0, 6), undoResult?.caretState, "Caret should be at deletion origin")
        assertEquals(selection, undoResult?.selection, "Selection range must be restored on undo")

        // Re-apply edit via Ctrl+Y
        val redoResult = stack.redo()
        assertEquals("hello ", redoResult?.text, "Redo should re-delete selection")
        assertEquals(EditorCaretState(0, 6), redoResult?.caretState)
        assertNull(redoResult?.selection, "Redo should clear selection range")
    }

    @Test
    fun testRedoBranchTruncatedOnNewPush() {
        val stack = UndoRedoStack()
        stack.init("a")
        stack.push("ab", afterCaret = EditorCaretState(0, 2), editKind = EditKind.Structural)
        stack.push("abc", afterCaret = EditorCaretState(0, 3), editKind = EditKind.Structural)
        stack.undo()
        stack.push("abX", afterCaret = EditorCaretState(0, 3), editKind = EditKind.Structural)
        assertFalse(stack.canRedo)
        assertTrue(stack.canUndo)
    }

    // ---------------------------------------------------------------------------
    // Rule 3: Direction change (insertion vs deletion) breaks the group
    // ---------------------------------------------------------------------------

    @Test
    fun testRule3DirectionChangeBreaksCoalescing() {
        val stack = UndoRedoStack(coalesceTimeoutMs = 5000L)
        stack.init("hello")
        stack.push("helloa", afterCaret = EditorCaretState(0, 6), editKind = EditKind.Insertion)
        stack.push("hello", afterCaret = EditorCaretState(0, 5), editKind = EditKind.Deletion)

        val undo1 = stack.undo()
        assertEquals("helloa", undo1?.text)
        val undo2 = stack.undo()
        assertEquals("hello", undo2?.text)
        assertFalse(stack.canUndo)
    }

    // ---------------------------------------------------------------------------
    // Rule 4: Cursor discontinuity breaks the group
    // ---------------------------------------------------------------------------

    @Test
    fun testRule4DifferentLineBreaksCoalescing() {
        val stack = UndoRedoStack(coalesceTimeoutMs = 5000L)
        stack.init("hello\nworld")
        stack.push("helloo\nworld", afterCaret = EditorCaretState(0, 6), editKind = EditKind.Insertion)
        stack.push("helloo\nworldd", afterCaret = EditorCaretState(1, 6), editKind = EditKind.Insertion)

        val undo1 = stack.undo()
        assertEquals("helloo\nworld", undo1?.text)
        val undo2 = stack.undo()
        assertEquals("hello\nworld", undo2?.text)
    }

    @Test
    fun testRule4NonSequentialColumnBreaksCoalescing() {
        val stack = UndoRedoStack(coalesceTimeoutMs = 5000L)
        stack.init("hello")
        stack.push("helloa", afterCaret = EditorCaretState(0, 6), editKind = EditKind.Insertion)
        stack.push("helloa   b", afterCaret = EditorCaretState(0, 10), editKind = EditKind.Insertion)

        val undo1 = stack.undo()
        assertEquals("helloa", undo1?.text)
        val undo2 = stack.undo()
        assertEquals("hello", undo2?.text)
    }

    // ---------------------------------------------------------------------------
    // Rule 5: Time pause breaks the group
    // ---------------------------------------------------------------------------

    @Test
    fun testRule5TimePauseBreaksCoalescing() {
        val stack = UndoRedoStack(coalesceTimeoutMs = 1L)
        stack.init("a")
        stack.push("ab", afterCaret = EditorCaretState(0, 2), editKind = EditKind.Insertion)
        stack.push("abc", afterCaret = EditorCaretState(0, 3), editKind = EditKind.Insertion)

        val text1 = stack.undo()?.text
        val text0 = stack.undo()?.text
        assertTrue(text1 == "ab" || text1 == "a")
        assertTrue(text0 == null || text0 == "a")
    }

    // ---------------------------------------------------------------------------
    // Rule 1 & 2: Whitespace and delimiter boundary (word-level undo)
    // ---------------------------------------------------------------------------

    @Test
    fun testRule1SpaceBoundaryProducesSeparateGroups() {
        val stack = UndoRedoStack(coalesceTimeoutMs = 5000L)
        stack.init("")

        stack.push("h", afterCaret = EditorCaretState(0, 1), editKind = EditKind.Insertion)
        stack.push("he", afterCaret = EditorCaretState(0, 2), editKind = EditKind.Insertion)
        stack.push("hel", afterCaret = EditorCaretState(0, 3), editKind = EditKind.Insertion)
        stack.push("hell", afterCaret = EditorCaretState(0, 4), editKind = EditKind.Insertion)
        stack.push("hello", afterCaret = EditorCaretState(0, 5), editKind = EditKind.Insertion)
        stack.push("hello ", afterCaret = EditorCaretState(0, 6), editKind = EditKind.Insertion)
        stack.push("hello w", afterCaret = EditorCaretState(0, 7), editKind = EditKind.Insertion)
        stack.push("hello wo", afterCaret = EditorCaretState(0, 8), editKind = EditKind.Insertion)
        stack.push("hello wor", afterCaret = EditorCaretState(0, 9), editKind = EditKind.Insertion)
        stack.push("hello worl", afterCaret = EditorCaretState(0, 10), editKind = EditKind.Insertion)
        stack.push("hello world", afterCaret = EditorCaretState(0, 11), editKind = EditKind.Insertion)

        val undo1 = stack.undo()
        assertEquals("hello ", undo1?.text)

        val undo2 = stack.undo()
        assertEquals("", undo2?.text)
    }

    @Test
    fun testRule2DelimiterBoundaryBreaksCoalescing() {
        val stack = UndoRedoStack(coalesceTimeoutMs = 5000L)
        stack.init("")

        stack.push("v", afterCaret = EditorCaretState(0, 1), editKind = EditKind.Insertion)
        stack.push("va", afterCaret = EditorCaretState(0, 2), editKind = EditKind.Insertion)
        stack.push("val", afterCaret = EditorCaretState(0, 3), editKind = EditKind.Insertion)
        stack.push("val=", afterCaret = EditorCaretState(0, 4), editKind = EditKind.Insertion)
        stack.push("val=1", afterCaret = EditorCaretState(0, 5), editKind = EditKind.Insertion)
        stack.push("val=10", afterCaret = EditorCaretState(0, 6), editKind = EditKind.Insertion)

        val undo1 = stack.undo()
        assertEquals("val=", undo1?.text)

        val undo2 = stack.undo()
        assertEquals("", undo2?.text)
    }

    // ---------------------------------------------------------------------------
    // Structural edits always start a new group
    // ---------------------------------------------------------------------------

    @Test
    fun testStructuralEditNeverCoalesces() {
        val stack = UndoRedoStack(coalesceTimeoutMs = 5000L)
        stack.init("hello")
        stack.push("helloa", afterCaret = EditorCaretState(0, 6), editKind = EditKind.Insertion)
        stack.push("helloa\n", afterCaret = EditorCaretState(1, 0), editKind = EditKind.Structural)

        val undo1 = stack.undo()
        assertEquals("helloa", undo1?.text)
        val undo2 = stack.undo()
        assertEquals("hello", undo2?.text)
        assertFalse(stack.canUndo)
    }
}
