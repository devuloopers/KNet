package com.devuloopers.knet.ui.desktop.codeeditor

import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.DocumentPreviewGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentPreviewGeneratorTest {

    @Test
    fun `generatePreview returns full text when line count is below max`() {
        val sampleText = "line 1\nline 2\nline 3"
        val result = DocumentPreviewGenerator.generatePreview(sampleText, maxPreviewLines = 10)

        assertEquals("line 1\nline 2\nline 3", result.previewText)
        assertEquals(3, result.totalLines)
        assertEquals(3, result.previewLines)
        assertFalse(result.isTruncated)
    }

    @Test
    fun `generatePreview truncates correctly at exactly maxPreviewLines`() {
        val sampleText = (1..100).joinToString("\n") { "line $it" }
        val result = DocumentPreviewGenerator.generatePreview(sampleText, maxPreviewLines = 10)

        assertEquals(100, result.totalLines)
        assertEquals(10, result.previewLines)
        assertTrue(result.isTruncated)
        val previewLines = result.previewText.lines()
        assertEquals(10, previewLines.size)
        assertEquals("line 1", previewLines.first())
        assertEquals("line 10", previewLines.last())
    }

    @Test
    fun `generatePreview handles empty text gracefully`() {
        val result = DocumentPreviewGenerator.generatePreview("", maxPreviewLines = 10)

        assertEquals("", result.previewText)
        assertEquals(1, result.totalLines)
        assertEquals(1, result.previewLines)
        assertFalse(result.isTruncated)
    }
}
