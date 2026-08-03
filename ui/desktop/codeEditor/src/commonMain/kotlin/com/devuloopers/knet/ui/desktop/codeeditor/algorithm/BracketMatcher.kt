package com.devuloopers.knet.ui.desktop.codeeditor.algorithm

import kotlin.math.max
import kotlin.math.min

/**
 * Result representing a matching bracket pair in a document.
 *
 * @property openOffset Char offset of the opening bracket.
 * @property closeOffset Char offset of the closing bracket.
 */
data class BracketMatchResult(
    val openOffset: Int,
    val closeOffset: Int
)

/**
 * Fast bracket pair matching engine inspired by RSyntaxTextArea RSyntaxUtilities.
 * Scans for matching '()', '[]', and '{}' pairs relative to the current caret offset,
 * enforcing a 10,000-character search boundary cap for instant sub-millisecond performance.
 */
object BracketMatcher {

    private const val MAX_SEARCH_DISTANCE = 10000

    /**
     * Finds the matching open/close bracket offset relative to the caret offset.
     *
     * @param text Full document text string.
     * @param caretOffset Current caret position offset in [text].
     * @return [BracketMatchResult] if a valid match is found, or `null` otherwise.
     */
    fun findMatch(text: String, caretOffset: Int): BracketMatchResult? {
        if (text.isEmpty() || caretOffset < 0 || caretOffset > text.length) return null

        val charAtCaret = if (caretOffset < text.length) text[caretOffset] else ' '
        val charBeforeCaret = if (caretOffset > 0) text[caretOffset - 1] else ' '

        val targetChar: Char
        val targetOffset: Int

        when {
            isOpenBracket(charAtCaret) || isCloseBracket(charAtCaret) -> {
                targetChar = charAtCaret
                targetOffset = caretOffset
            }
            isOpenBracket(charBeforeCaret) || isCloseBracket(charBeforeCaret) -> {
                targetChar = charBeforeCaret
                targetOffset = caretOffset - 1
            }
            else -> return null
        }

        val matchingChar = getMatchingChar(targetChar) ?: return null

        return if (isOpenBracket(targetChar)) {
            // Scan forward for closing bracket
            val searchEnd = min(text.length, targetOffset + MAX_SEARCH_DISTANCE)
            var depth = 1
            for (index in (targetOffset + 1) until searchEnd) {
                val current = text[index]
                if (current == targetChar) {
                    depth++
                } else if (current == matchingChar) {
                    depth--
                    if (depth == 0) {
                        return BracketMatchResult(openOffset = targetOffset, closeOffset = index)
                    }
                }
            }
            null
        } else {
            // Scan backward for opening bracket
            val searchStart = max(0, targetOffset - MAX_SEARCH_DISTANCE)
            var depth = 1
            for (index in (targetOffset - 1) downTo searchStart) {
                val current = text[index]
                if (current == targetChar) {
                    depth++
                } else if (current == matchingChar) {
                    depth--
                    if (depth == 0) {
                        return BracketMatchResult(openOffset = index, closeOffset = targetOffset)
                    }
                }
            }
            null
        }
    }

    private fun isOpenBracket(char: Char): Boolean = char == '{' || char == '[' || char == '('

    private fun isCloseBracket(char: Char): Boolean = char == '}' || char == ']' || char == ')'

    private fun getMatchingChar(char: Char): Char? = when (char) {
        '{' -> '}'
        '}' -> '{'
        '[' -> ']'
        ']' -> '['
        '(' -> ')'
        ')' -> '('
        else -> null
    }
}
