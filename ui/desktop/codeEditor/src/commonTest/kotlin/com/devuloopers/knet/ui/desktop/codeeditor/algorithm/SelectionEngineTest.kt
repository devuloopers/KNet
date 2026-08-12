package com.devuloopers.knet.ui.desktop.codeeditor.algorithm

import com.devuloopers.knet.ui.desktop.codeeditor.model.EditorSelection
import kotlin.test.Test
import kotlin.test.assertEquals

class SelectionEngineTest {

    @Test
    fun testExtractSelectedTextUnfolded() {
        val lines = listOf("line 0", "line 1", "line 2")
        val buffer = DocumentBuffer(lines)
        val selection = EditorSelection(startLine = 0, startCol = 2, endLine = 2, endCol = 4)

        val extracted = SelectionEngine.extractSelectedText(buffer, selection)
        assertEquals("ne 0\nline 1\nline", extracted)
    }

    @Test
    fun testExtractSelectedTextFoldAwareIncludesEntireCollapsedBlock() {
        val lines = listOf(
            "fun main() {",       // line 0 (collapsed start)
            "    val a = 1",       // line 1 (hidden)
            "    val b = 2",       // line 2 (hidden)
            "}",                   // line 3 (hidden fold end)
            "println(\"done\")"   // line 4
        )
        val buffer = DocumentBuffer(lines)
        val foldRegion = FoldRegion(startLine = 0, endLine = 3, closingSymbol = "}")
        val collapsedFoldStarts = setOf(0)

        // Select fold header (line 0)
        val selection = EditorSelection(startLine = 0, startCol = 0, endLine = 0, endCol = 12)

        val extracted = SelectionEngine.extractSelectedText(
            buffer = buffer,
            selection = selection,
            foldRegions = listOf(foldRegion),
            collapsedFoldStartLines = collapsedFoldStarts
        )

        val expected = "fun main() {\n    val a = 1\n    val b = 2\n}"
        assertEquals(expected, extracted, "Extracting collapsed line 0 must copy 100% of the folded block (lines 0..3)")
    }

    @Test
    fun testDeleteSelectedTextFoldAwareDeletesEntireCollapsedBlock() {
        val lines = listOf(
            "fun main() {",       // line 0 (collapsed start)
            "    val a = 1",       // line 1 (hidden)
            "    val b = 2",       // line 2 (hidden)
            "}",                   // line 3 (hidden fold end)
            "println(\"done\")"   // line 4
        )
        val buffer = DocumentBuffer(lines)
        val foldRegion = FoldRegion(startLine = 0, endLine = 3, closingSymbol = "}")
        val collapsedFoldStarts = setOf(0)

        val selection = EditorSelection(startLine = 0, startCol = 0, endLine = 0, endCol = 12)

        SelectionEngine.deleteSelectedText(
            buffer = buffer,
            selection = selection,
            foldRegions = listOf(foldRegion),
            collapsedFoldStartLines = collapsedFoldStarts
        )

        val remaining = buffer.getLines()
        assertEquals(2, remaining.size)
        assertEquals("", remaining[0])
        assertEquals("println(\"done\")", remaining[1])
    }
}
