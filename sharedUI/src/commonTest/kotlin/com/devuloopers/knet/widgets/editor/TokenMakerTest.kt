package com.devuloopers.knet.widgets.editor

import com.devuloopers.knet.editor.highlighter.TokenMaker
import com.devuloopers.knet.editor.highlighter.TokenState
import kotlin.test.Test
import kotlin.test.assertEquals


class TokenMakerTest {

    @Test
    fun testMultilineCommentStatePropagation() {
        val line1 = "/* Start multiline comment"
        val line2 = "   Middle line of comment"
        val line3 = "   End comment */ const x = 10"

        val res1 = TokenMaker.tokenizeLine(line1, TokenState.NULL)
        assertEquals(TokenState.IN_MULTILINE_COMMENT, res1.endState)

        val res2 = TokenMaker.tokenizeLine(line2, res1.endState)
        assertEquals(TokenState.IN_MULTILINE_COMMENT, res2.endState)

        val res3 = TokenMaker.tokenizeLine(line3, res2.endState)
        assertEquals(TokenState.NULL, res3.endState)
    }

    @Test
    fun testSingleLineComment() {
        val line = "// This is a single line comment"
        val res = TokenMaker.tokenizeLine(line, TokenState.NULL)
        assertEquals(TokenState.NULL, res.endState)
    }
}
