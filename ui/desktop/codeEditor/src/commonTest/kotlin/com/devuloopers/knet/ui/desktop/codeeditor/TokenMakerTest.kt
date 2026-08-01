package com.devuloopers.knet.ui.desktop.codeeditor

import com.devuloopers.knet.ui.desktop.codeeditor.syntax.tokenizer.TokenMaker
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [TokenMaker] — zero-allocation character classification utilities.
 */
class TokenMakerTest {

    @Test
    fun testZeroAllocationWhitespaceCheckReturnsTrueForWhitespace() {
        val line = "    \"key\":    \"value\""
        // Characters 0..3 are leading spaces (whitespace only)
        assertTrue(
            TokenMaker.isOnlyWhitespaceBetween(line, 0, 4),
            "Leading spaces should be whitespace-only"
        )
    }

    @Test
    fun testZeroAllocationWhitespaceCheckReturnsFalseForNonWhitespace() {
        val line = "\"key\": \"value\""
        assertFalse(
            TokenMaker.isOnlyWhitespaceBetween(line, 0, 5),
            "Should detect non-whitespace characters in slice"
        )
    }

    @Test
    fun testEmptyRangeReturnsTrue() {
        val line = "anything"
        assertTrue(
            TokenMaker.isOnlyWhitespaceBetween(line, 3, 3),
            "Empty range should return true (no non-whitespace found)"
        )
    }
}
