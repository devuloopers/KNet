package com.devuloopers.knet.ui.desktop.codeeditor.component

import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorPosition
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorSelection
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies selection-aware viewport reveal behavior independently from Compose scrolling. */
class CaretRevealPolicyTest {

    private val documentEnd = EditorPosition(line = 20, column = 14)

    @Test
    fun caretOnlyAndPartialSelectionsRevealTheirActivePosition() {
        assertTrue(shouldRevealCaretForSelection(selection = null, documentEnd = documentEnd))
        assertTrue(
            shouldRevealCaretForSelection(
                selection = EditorSelection(
                    anchor = EditorPosition(line = 4, column = 2),
                    active = EditorPosition(line = 8, column = 5)
                ),
                documentEnd = documentEnd
            )
        )
    }

    @Test
    fun wholeDocumentSelectionKeepsViewportInEitherDirection() {
        assertFalse(
            shouldRevealCaretForSelection(
                selection = EditorSelection(
                    anchor = EditorPosition(0, 0),
                    active = documentEnd
                ),
                documentEnd = documentEnd
            )
        )
        assertFalse(
            shouldRevealCaretForSelection(
                selection = EditorSelection(
                    anchor = documentEnd,
                    active = EditorPosition(0, 0)
                ),
                documentEnd = documentEnd
            )
        )
    }
}
