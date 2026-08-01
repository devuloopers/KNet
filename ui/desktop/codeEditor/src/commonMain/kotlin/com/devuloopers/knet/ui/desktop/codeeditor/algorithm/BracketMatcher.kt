package com.devuloopers.knet.ui.desktop.codeeditor.algorithm

/**
 * Structural bracket and parenthetical matching engine.
 */
internal object BracketMatcher {

    private val OPEN_BRACKETS = charArrayOf('{', '[', '(')
    private val CLOSE_BRACKETS = charArrayOf('}', ']', ')')

    fun findMatchingBracket(text: String, caretOffset: Int): Int? {
        if (caretOffset !in 0 until text.length) return null

        val char = text[caretOffset]
        val openIndex = OPEN_BRACKETS.indexOf(char)
        if (openIndex != -1) {
            val matchingClose = CLOSE_BRACKETS[openIndex]
            var depth = 1
            for (i in (caretOffset + 1) until text.length) {
                if (text[i] == char) depth++
                else if (text[i] == matchingClose) {
                    depth--
                    if (depth == 0) return i
                }
            }
            return null
        }

        val closeIndex = CLOSE_BRACKETS.indexOf(char)
        if (closeIndex != -1) {
            val matchingOpen = OPEN_BRACKETS[closeIndex]
            var depth = 1
            for (i in (caretOffset - 1) downTo 0) {
                if (text[i] == char) depth++
                else if (text[i] == matchingOpen) {
                    depth--
                    if (depth == 0) return i
                }
            }
            return null
        }

        return null
    }
}
