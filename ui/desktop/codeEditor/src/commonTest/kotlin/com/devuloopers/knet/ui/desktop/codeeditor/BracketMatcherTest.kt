package com.devuloopers.knet.ui.desktop.codeeditor

import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.BracketMatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit tests for [BracketMatcher] — finds matching bracket positions.
 */
class BracketMatcherTest {

    @Test
    fun testFindMatchingCurlyBrackets() {
        val text = "{\n  \"data\": {\n    \"id\": 1\n  }\n}"
        val closePos = BracketMatcher.findMatchingBracket(text, 0)
        assertNotNull(closePos)
        assertEquals(text.lastIndexOf('}'), closePos)
    }

    @Test
    fun testFindMatchingSquareBrackets() {
        val text = "val arr = [1, 2, 3]"
        val openPos = text.indexOf('[')
        val closePos = BracketMatcher.findMatchingBracket(text, openPos)
        assertNotNull(closePos)
        assertEquals(text.indexOf(']'), closePos)
    }

    @Test
    fun testFindMatchingParentheses() {
        val text = "function test(a, b) {"
        val openPos = text.indexOf('(')
        val closePos = BracketMatcher.findMatchingBracket(text, openPos)
        assertNotNull(closePos)
        assertEquals(text.indexOf(')'), closePos)
    }

    @Test
    fun testNoMatchReturnsNull() {
        val text = "{ unclosed"
        val closePos = BracketMatcher.findMatchingBracket(text, 0)
        assertNull(closePos)
    }
}
