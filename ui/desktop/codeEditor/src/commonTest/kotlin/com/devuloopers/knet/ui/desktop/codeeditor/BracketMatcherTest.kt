package com.devuloopers.knet.ui.desktop.codeeditor

import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.BracketMatcher
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.BracketMatchResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


class BracketMatcherTest {

    @Test
    fun testFindMatchingCurlyBrackets() {
        val text = "{\n  \"data\": {\n    \"id\": 1\n  }\n}"
        val caretOffset = 13 // after second '{'

        val match = BracketMatcher.findMatch(text, caretOffset)
        assertNotNull(match)
        assertEquals(12, match.openOffset)
        assertEquals(28, match.closeOffset)
    }



    @Test
    fun testFindMatchingSquareBrackets() {
        val text = "val arr = [1, 2, 3]"
        val caretOffset = 10 // right after '['

        val match = BracketMatcher.findMatch(text, caretOffset)
        assertNotNull(match)
        assertEquals(10, match.openOffset)
        assertEquals(18, match.closeOffset)
    }

    @Test
    fun testFindMatchingParentheses() {
        val text = "function test(a, b) {"
        val caretOffset = 13 // right after '('

        val match = BracketMatcher.findMatch(text, caretOffset)
        assertNotNull(match)
        assertEquals(13, match.openOffset)
        assertEquals(18, match.closeOffset)
    }
}

