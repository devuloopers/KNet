package com.devuloopers.knet.ui.desktop.codeeditor.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class EditorScalabilityTest {
    @Test
    fun editingOneOfOneHundredThousandLinesReusesEveryUnaffectedChunk() {
        val document = ChunkedEditorDocument(
            initialText = (0 until 100_000).joinToString("\n") { "line-$it" },
            chunkSize = 256
        )
        val before = document.snapshot

        document.apply(
            EditorTextEdit(
                range = EditorRange(EditorPosition(50_000, 5), EditorPosition(50_000, 6)),
                replacement = "X",
                kind = EditorEditKind.Replacement
            )
        )
        val after = document.snapshot

        assertEquals(100_000, after.lineCount)
        assertEquals("line-X0000", after.line(50_000))
        assertEquals(before.chunks.size, after.chunks.size)
        val changedChunkCount = before.chunks.indices.count { before.chunks[it] !== after.chunks[it] }
        assertEquals(1, changedChunkCount)
        assertSame(before.chunks.first(), after.chunks.first())
        assertSame(before.chunks.last(), after.chunks.last())
    }
}
