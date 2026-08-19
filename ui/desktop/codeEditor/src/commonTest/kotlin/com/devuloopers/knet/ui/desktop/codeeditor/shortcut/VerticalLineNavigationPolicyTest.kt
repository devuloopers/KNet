package com.devuloopers.knet.ui.desktop.codeeditor.shortcut

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerticalLineNavigationPolicyTest {
    @Test
    fun wrappedMiddleRowsKeepVerticalNavigationInsideTheTextField() {
        assertFalse(
            VerticalLineNavigationPolicy.shouldMoveToAdjacentLogicalLine(
                direction = VerticalNavigationDirection.Up,
                isWordWrapEnabled = true,
                currentVisualLineIndex = 1,
                visualLineCount = 3
            )
        )
        assertFalse(
            VerticalLineNavigationPolicy.shouldMoveToAdjacentLogicalLine(
                direction = VerticalNavigationDirection.Down,
                isWordWrapEnabled = true,
                currentVisualLineIndex = 1,
                visualLineCount = 3
            )
        )
    }

    @Test
    fun wrappedBoundaryRowsMoveToAdjacentLogicalLines() {
        assertTrue(
            VerticalLineNavigationPolicy.shouldMoveToAdjacentLogicalLine(
                direction = VerticalNavigationDirection.Up,
                isWordWrapEnabled = true,
                currentVisualLineIndex = 0,
                visualLineCount = 3
            )
        )
        assertTrue(
            VerticalLineNavigationPolicy.shouldMoveToAdjacentLogicalLine(
                direction = VerticalNavigationDirection.Down,
                isWordWrapEnabled = true,
                currentVisualLineIndex = 2,
                visualLineCount = 3
            )
        )
    }

    @Test
    fun nonWrappedOrUnmeasuredLinesUseLogicalNavigation() {
        assertTrue(
            VerticalLineNavigationPolicy.shouldMoveToAdjacentLogicalLine(
                direction = VerticalNavigationDirection.Down,
                isWordWrapEnabled = false,
                currentVisualLineIndex = 0,
                visualLineCount = 1
            )
        )
        assertTrue(
            VerticalLineNavigationPolicy.shouldMoveToAdjacentLogicalLine(
                direction = VerticalNavigationDirection.Up,
                isWordWrapEnabled = true,
                currentVisualLineIndex = null,
                visualLineCount = 3
            )
        )
    }
}
