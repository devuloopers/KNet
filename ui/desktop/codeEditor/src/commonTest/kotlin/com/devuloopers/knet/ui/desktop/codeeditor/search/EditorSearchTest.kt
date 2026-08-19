package com.devuloopers.knet.ui.desktop.codeeditor.search

import com.devuloopers.knet.ui.desktop.codeeditor.concurrency.EditorCancellationCheckpoint
import com.devuloopers.knet.ui.desktop.codeeditor.document.ChunkedEditorDocument
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorPosition
import com.devuloopers.knet.ui.desktop.codeeditor.session.EditorSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorSearchTest {
    @Test
    fun searchPreservesOriginalLineCoordinates() {
        val snapshot = ChunkedEditorDocument("first\nmatch here\nlast match").snapshot

        val result = EditorSearchEngine.search(snapshot, EditorSearchOptions(query = "match"))

        assertEquals(2, result.matches.size)
        assertEquals(EditorPosition(1, 0), result.matches[0].range.start)
        assertEquals(EditorPosition(2, 5), result.matches[1].range.start)
        assertNull(result.failure)
    }

    @Test
    fun wholeWordAndCaseOptionsAreRespected() {
        val snapshot = ChunkedEditorDocument("cat catalog CAT").snapshot

        val insensitive = EditorSearchEngine.search(
            snapshot,
            EditorSearchOptions(query = "cat", wholeWord = true)
        )
        val sensitive = EditorSearchEngine.search(
            snapshot,
            EditorSearchOptions(query = "cat", matchCase = true, wholeWord = true)
        )

        assertEquals(2, insensitive.matches.size)
        assertEquals(1, sensitive.matches.size)
    }

    @Test
    fun invalidRegexReturnsTypedFailure() {
        val result = EditorSearchEngine.search(
            ChunkedEditorDocument("value").snapshot,
            EditorSearchOptions(query = "[", useRegularExpression = true)
        )

        assertTrue(result.matches.isEmpty())
        assertIs<EditorSearchFailure.InvalidRegularExpression>(result.failure)
    }

    @Test
    fun replaceAllIsOneUndoableBatch() {
        val session = EditorSession("one value\ntwo value\nvalue")
        val search = EditorSearchSession()
        search.update(session.snapshot, EditorSearchOptions("value"))

        assertEquals(3, search.replaceAll(session, "item"))
        assertEquals("one item\ntwo item\nitem", session.snapshot.text())
        assertTrue(session.undo())
        assertEquals("one value\ntwo value\nvalue", session.snapshot.text())
    }

    @Test
    fun largeSearchObservesCooperativeCancellationDuringScan() {
        val snapshot = ChunkedEditorDocument((0 until 1_000).joinToString("\n") { "line-$it" }).snapshot
        var checkpointCount = 0

        assertFailsWith<IllegalStateException> {
            EditorSearchEngine.search(
                snapshot,
                EditorSearchOptions("line"),
                EditorCancellationCheckpoint {
                    checkpointCount++
                    if (checkpointCount == 2) error("cancelled")
                }
            )
        }

        assertEquals(2, checkpointCount)
    }
}
