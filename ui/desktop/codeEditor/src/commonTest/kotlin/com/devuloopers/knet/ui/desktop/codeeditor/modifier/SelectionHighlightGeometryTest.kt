package com.devuloopers.knet.ui.desktop.codeeditor.modifier

import kotlin.test.Test
import kotlin.test.assertEquals

class SelectionHighlightGeometryTest {
    @Test
    fun singleVisualLineConsumesTheCompleteRowWithoutChangingTextMetrics() {
        val bounds = selectionVerticalBounds(
            visualLineIndex = 0,
            visualLineCount = 1,
            lineTop = 0f,
            lineBottom = 14f,
            previousLineBottom = null,
            nextLineTop = null,
            contentHeight = 14f,
            containerHeight = 16f
        )

        assertEquals(0f, bounds.top)
        assertEquals(16f, bounds.bottom)
        assertEquals(16f, bounds.height)
    }

    @Test
    fun adjacentWrappedVisualLinesShareOneBoundaryAcrossInternalLeading() {
        val first = selectionVerticalBounds(
            visualLineIndex = 0,
            visualLineCount = 2,
            lineTop = 0f,
            lineBottom = 13f,
            previousLineBottom = null,
            nextLineTop = 15f,
            contentHeight = 28f,
            containerHeight = 32f
        )
        val second = selectionVerticalBounds(
            visualLineIndex = 1,
            visualLineCount = 2,
            lineTop = 15f,
            lineBottom = 28f,
            previousLineBottom = 13f,
            nextLineTop = null,
            contentHeight = 28f,
            containerHeight = 32f
        )

        assertEquals(16f, first.bottom)
        assertEquals(first.bottom, second.top)
    }

    @Test
    fun outerLeadingIsAssignedToTheFirstAndLastSelectionSlots() {
        val first = selectionVerticalBounds(
            visualLineIndex = 0,
            visualLineCount = 2,
            lineTop = 0f,
            lineBottom = 14f,
            previousLineBottom = null,
            nextLineTop = 14f,
            contentHeight = 28f,
            containerHeight = 32f
        )
        val second = selectionVerticalBounds(
            visualLineIndex = 1,
            visualLineCount = 2,
            lineTop = 14f,
            lineBottom = 28f,
            previousLineBottom = 14f,
            nextLineTop = null,
            contentHeight = 28f,
            containerHeight = 32f
        )

        assertEquals(0f, first.top)
        assertEquals(32f, second.bottom)
    }
}
