package com.devuloopers.knet.ui.desktop.codeeditor.syntax.language

import androidx.compose.ui.text.AnnotatedString
import com.devuloopers.knet.ui.desktop.codeeditor.syntax.tokenizer.TokenMaker
import com.devuloopers.knet.ui.desktop.codeeditor.syntax.tokenizer.TokenType

/**
 * Base HTML/XML markup fold and syntax highlighter.
 *
 * Implements [CodeLanguageHighlighter] for tag-based markup languages.
 * Self-closing HTML void elements (meta, br, hr, img, input, etc.) are
 * never pushed onto the fold stack.
 */
internal open class TagMarkupHighlighter(override val languageId: String) : CodeLanguageHighlighter {

    /**
     * HTML void elements that must never be pushed to the opening-tag fold stack.
     * These elements have no closing tag in HTML5.
     */
    private val voidElements = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input",
        "link", "meta", "param", "source", "track", "wbr"
    )

    override fun calculateFoldRanges(lines: List<String>): Map<Int, Int> {
        val result = mutableMapOf<Int, Int>()
        // Stack of (tagName, openLineIndex)
        val tagStack = mutableListOf<Pair<String, Int>>()

        for (index in lines.indices) {
            val line = lines[index].trim()
            when {
                // Self-closing tag (e.g. <br/>, <img src="..." />) — skip
                line.endsWith("/>") -> Unit

                // Closing tag (e.g. </HEAD>)
                line.startsWith("</") -> {
                    val closeTagEnd = line.indexOf('>')
                    val closeTagName = if (closeTagEnd != -1) {
                        line.substring(2, closeTagEnd).trim().lowercase()
                    } else {
                        ""
                    }
                    // Search the stack from top for matching tag name
                    val matchIndex = tagStack.indexOfLast { it.first.lowercase() == closeTagName }
                    if (matchIndex != -1) {
                        val (_, start) = tagStack.removeAt(matchIndex)
                        if (index > start) result[start] = index
                    } else if (tagStack.isNotEmpty()) {
                        // Fallback: close whatever is on top (handles malformed markup)
                        val (_, start) = tagStack.removeAt(tagStack.lastIndex)
                        if (index > start) result[start] = index
                    }
                }

                // Comment — skip
                line.startsWith("<!--") -> Unit

                // Opening tag — extract name and push if not a void element
                line.startsWith("<") -> {
                    val spaceIndex = line.indexOf(' ')
                    val closeBracket = line.indexOf('>')
                    val endName = when {
                        spaceIndex != -1 && closeBracket != -1 -> minOf(spaceIndex, closeBracket)
                        spaceIndex != -1 -> spaceIndex
                        closeBracket != -1 -> closeBracket
                        else -> line.length
                    }
                    if (endName > 1) {
                        val tagName = line.substring(1, endName).trim()
                        if (tagName.lowercase() !in voidElements) {
                            tagStack.add(tagName to index)
                        }
                    }
                }
            }
        }
        return result
    }

    override fun resolveClosingSymbol(lines: List<String>, endLineIndex: Int): String = "</"

    override fun highlightLine(lineText: String): AnnotatedString {
        if (lineText.isBlank()) return AnnotatedString(lineText)

        val tokens = mutableListOf<Pair<IntRange, TokenType>>()
        var idx = 0
        val len = lineText.length

        while (idx < len) {
            if (lineText[idx] == '<') {
                val closeTag = lineText.indexOf('>', idx)
                if (closeTag != -1) {
                    tokens.add(IntRange(idx, closeTag) to TokenType.TAG_NAME)
                    idx = closeTag + 1
                } else {
                    tokens.add(IntRange(idx, len - 1) to TokenType.TAG_NAME)
                    idx = len
                }
            } else {
                idx++
            }
        }

        return TokenMaker.buildAnnotatedStringFromTokens(tokens, lineText)
    }
}
