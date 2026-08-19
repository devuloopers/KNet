package com.devuloopers.knet.ui.desktop.codeeditor.viewport

import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.FoldRegion
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.LineFoldState
import com.devuloopers.knet.ui.desktop.codeeditor.document.ChunkedEditorDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EditorVisualLineMapTest {
    @Test
    fun identityMappingSupportsLargeDocumentsWithoutChangingCoordinates() {
        val map = EditorVisualLineMap.build(
            documentLineCount = 100_000,
            foldRegions = emptyList(),
            collapsedStarts = emptySet()
        )

        assertEquals(100_000, map.visibleLineCount)
        assertEquals(99_999, map.toDocumentLine(99_999))
        assertEquals(99_999, map.toVisibleLine(99_999))
    }

    @Test
    fun collapsedNestedRegionsProduceOneStableLogicalToVisualMap() {
        val map = EditorVisualLineMap.build(
            documentLineCount = 9,
            foldRegions = listOf(
                FoldRegion(1, 7, "}"),
                FoldRegion(3, 5, "]")
            ),
            collapsedStarts = setOf(1, 3)
        )

        assertEquals(3, map.visibleLineCount)
        assertEquals(listOf(0, 1, 8), (0 until map.visibleLineCount).map(map::toDocumentLine))
        assertNull(map.toVisibleLine(3))
        assertEquals(1, map.toVisibleLine(1))
    }

    @Test
    fun foldedDisplayTextIsProjectionAndDoesNotMutateDocument() {
        val snapshot = ChunkedEditorDocument("before\nobject {\nvalue\n}\nafter").snapshot
        val map = EditorVisualLineMap.build(
            documentLineCount = snapshot.lineCount,
            foldRegions = listOf(FoldRegion(1, 3, "}")),
            collapsedStarts = setOf(1)
        )

        val line = map.lazyLine(snapshot, 1)

        assertEquals(LineFoldState.FoldStartCollapsed, line.foldState)
        assertEquals("object { ... }", line.displayText)
        assertEquals("object {", snapshot.line(1))
    }
}
