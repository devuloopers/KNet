package com.devuloopers.knet.ui.desktop.codeeditor.algorithm

import kotlin.test.Test
import kotlin.test.assertEquals

class WordBoundaryEngineTest {

    @Test
    fun testWordBoundaryOnAlphanumericToken() {
        val lineText = "val formattedQuotes = listOf()"

        // Double-click on 'f' at col 4
        val (startCol1, endCol1) = WordBoundaryEngine.findWordBounds(lineText, 4)
        assertEquals(4, startCol1)
        assertEquals(19, endCol1)
        assertEquals("formattedQuotes", lineText.substring(startCol1, endCol1))

        // Double-click on 'Q' at col 13
        val (startCol2, endCol2) = WordBoundaryEngine.findWordBounds(lineText, 13)
        assertEquals(4, startCol2)
        assertEquals(19, endCol2)
        assertEquals("formattedQuotes", lineText.substring(startCol2, endCol2))
    }

    @Test
    fun testWordBoundaryOnSymbolAndWhitespace() {
        val lineText = "    \"data\": {"

        // Double-click on quote at col 4
        val (startCol, endCol) = WordBoundaryEngine.findWordBounds(lineText, 4)
        assertEquals(4, startCol)
        assertEquals(5, endCol)
        assertEquals("\"", lineText.substring(startCol, endCol))

        // Double-click on 'data' at col 6
        val (startData, endData) = WordBoundaryEngine.findWordBounds(lineText, 6)
        assertEquals(5, startData)
        assertEquals(9, endData)
        assertEquals("data", lineText.substring(startData, endData))
    }
}
