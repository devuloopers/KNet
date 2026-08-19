package com.devuloopers.knet.ui.desktop.codeeditor.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class ChunkedEditorDocumentTest {
    @Test
    fun singleLineEditCopiesOnlyAffectedChunk() {
        val initialText = (0 until 12).joinToString("\n") { "line-$it" }
        val document = ChunkedEditorDocument(initialText = initialText, chunkSize = 4)
        val before = document.snapshot

        val change = document.apply(
            EditorTextEdit(
                range = EditorRange(EditorPosition(5, 5), EditorPosition(5, 6)),
                replacement = "X",
                kind = EditorEditKind.Replacement
            )
        )
        val after = document.snapshot

        assertEquals(0L, change.beforeVersion)
        assertEquals(1L, change.afterVersion)
        assertEquals("line-X", after.line(5))
        assertSame(before.chunks[0], after.chunks[0])
        assertNotSame(before.chunks[1], after.chunks[1])
        assertSame(before.chunks[2], after.chunks[2])
    }

    @Test
    fun multilineReplacementPreservesPrefixAndSuffix() {
        val document = ChunkedEditorDocument("first\nsecond\nthird")

        val change = document.apply(
            EditorTextEdit(
                range = EditorRange(EditorPosition(0, 2), EditorPosition(2, 2)),
                replacement = "A\nB",
                kind = EditorEditKind.Structural
            )
        )

        assertEquals("rst\nsecond\nth", change.removedText)
        assertEquals("fiA\nBird", document.snapshot.text())
        assertEquals(EditorPosition(1, 1), change.afterRange.end)
    }

    @Test
    fun trailingNewlineProducesTrailingEmptyLogicalLine() {
        val document = ChunkedEditorDocument("one\ntwo\n")

        assertEquals(3, document.snapshot.lineCount)
        assertEquals("", document.snapshot.line(2))
        assertEquals("one\ntwo\n", document.snapshot.text())
    }

    @Test
    fun unchangedReplacementDoesNotAdvanceVersion() {
        val document = ChunkedEditorDocument("value")

        val change = document.apply(
            EditorTextEdit(
                range = EditorRange(EditorPosition(0, 0), EditorPosition(0, 5)),
                replacement = "value",
                kind = EditorEditKind.Replacement
            )
        )

        assertEquals(0L, change.afterVersion)
        assertEquals(0L, document.snapshot.version)
    }
}
