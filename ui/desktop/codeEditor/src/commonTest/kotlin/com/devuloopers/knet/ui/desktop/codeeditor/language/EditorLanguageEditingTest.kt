package com.devuloopers.knet.ui.desktop.codeeditor.language

import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorPosition
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorSelection
import com.devuloopers.knet.ui.desktop.codeeditor.session.EditorSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EditorLanguageEditingTest {
    @Test
    fun registeredBracketPairsDriveAutomaticClosingWithoutCoreBranching() {
        val session = EditorSession("value")
        val brackets = object : EditorBracketProvider {
            override val pairs: Set<EditorBracketPair> = setOf(EditorBracketPair('(', ')'))
        }

        assertTrue(EditorLanguageEditing.applyLineChange(session, 0, "value(", brackets))

        assertEquals("value()", session.snapshot.text())
        assertEquals(EditorPosition(0, 6), session.caret)
        assertTrue(session.undo())
        assertEquals("value", session.snapshot.text())
    }

    @Test
    fun lineCommentCapabilityAppliesOneUndoableBatchToSelection() {
        val session = EditorSession("first\n  second\nthird")
        session.select(
            EditorSelection(
                anchor = EditorPosition(0, 0),
                active = EditorPosition(2, 0)
            )
        )

        assertTrue(
            EditorLanguageEditing.toggleComment(
                session,
                EditorCommentConfiguration(linePrefix = "//")
            )
        )

        assertEquals("// first\n  // second\nthird", session.snapshot.text())
        assertTrue(session.undo())
        assertEquals("first\n  second\nthird", session.snapshot.text())
    }

    @Test
    fun blockCommentCapabilityWrapsAndUnwrapsCurrentLine() {
        val session = EditorSession("<section>value</section>")
        val comments = EditorCommentConfiguration(blockStart = "<!--", blockEnd = "-->")

        assertTrue(EditorLanguageEditing.toggleComment(session, comments))
        assertEquals("<!--<section>value</section>-->", session.snapshot.text())
        assertTrue(EditorLanguageEditing.toggleComment(session, comments))
        assertEquals("<section>value</section>", session.snapshot.text())
    }
}
