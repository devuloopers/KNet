package com.devuloopers.knet.ui.desktop.codeeditor

import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.DocumentBuffer
import kotlin.test.Test
import kotlin.test.assertEquals

class DocumentBufferTest {

    @Test
    fun testInitializationWithEmptyListGuaranteesOneLine() {
        val buffer = DocumentBuffer(emptyList())
        assertEquals(1, buffer.lineCount())
        assertEquals("", buffer.toFullText())
    }

    @Test
    fun testSetLine() {
        val buffer = DocumentBuffer(listOf("line0", "line1", "line2"))
        buffer.setLine(1, "updated line1")
        assertEquals(listOf("line0", "updated line1", "line2"), buffer.getLines())
        assertEquals("line0\nupdated line1\nline2", buffer.toFullText())
    }

    @Test
    fun testSplitLineMiddle() {
        val buffer = DocumentBuffer(listOf("hello world"))
        buffer.splitLine(index = 0, col = 6, trailingIndent = "  ")
        assertEquals(listOf("hello ", "  world"), buffer.getLines())
        assertEquals("hello \n  world", buffer.toFullText())
    }

    @Test
    fun testMergeLines() {
        val buffer = DocumentBuffer(listOf("hello", " world"))
        buffer.mergeLines(1)
        assertEquals(listOf("hello world"), buffer.getLines())
    }

    @Test
    fun testMergeLinesFirstLineIsNoOp() {
        val buffer = DocumentBuffer(listOf("line0", "line1"))
        buffer.mergeLines(0)
        assertEquals(listOf("line0", "line1"), buffer.getLines())
    }

    @Test
    fun testReplaceAll() {
        val buffer = DocumentBuffer(listOf("line0"))
        buffer.replaceAll(listOf("new0", "new1"))
        assertEquals(listOf("new0", "new1"), buffer.getLines())
    }
}
