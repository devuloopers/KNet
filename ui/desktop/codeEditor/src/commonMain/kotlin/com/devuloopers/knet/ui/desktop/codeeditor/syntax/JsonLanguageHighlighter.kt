package com.devuloopers.knet.ui.desktop.codeeditor.syntax

data class ParsedJsonKeyValue(
    val leadingIndent: String,
    val keyPart: String,
    val valPart: String
)

/**
 * Syntax highlighter strategy for JSON documents.
 */
class JsonLanguageHighlighter : CodeLanguageHighlighter {
    override val languageId: String = "json"

    /**
     * Robustly parses a JSON line into leading indent, keyPart (with quotes), and valPart.
     * Correctly handles keys containing colons (e.g. "google:entityinfo": "val").
     */
    fun parseJsonKeyValueLine(lineText: String): ParsedJsonKeyValue? {
        val leadingIndent = lineText.takeWhile { it.isWhitespace() }
        val trimmed = lineText.substring(leadingIndent.length)
        if (!trimmed.startsWith("\"")) return null

        var inKey = false
        var escaped = false
        var keyEndIndex = -1

        for (i in leadingIndent.length until lineText.length) {
            val ch = lineText[i]
            if (escaped) {
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else if (ch == '"') {
                if (!inKey) {
                    inKey = true
                } else {
                    keyEndIndex = i
                    break
                }
            }
        }
        if (keyEndIndex == -1) return null

        var colonIndex = -1
        for (i in (keyEndIndex + 1) until lineText.length) {
            if (lineText[i] == ':') {
                colonIndex = i
                break
            }
        }
        if (colonIndex == -1) return null

        val keyPart = lineText.substring(0, keyEndIndex + 1)
        val valPart = lineText.substring(colonIndex + 1)
        return ParsedJsonKeyValue(leadingIndent, keyPart, valPart)
    }

    override fun calculateFoldRanges(lines: List<String>): Map<Int, Int> {
        val foldRanges = mutableMapOf<Int, Int>()
        val stack = ArrayDeque<Pair<Int, Char>>()

        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            var inString = false
            var escaped = false

            for (ch in trimmed) {
                when {
                    escaped -> escaped = false
                    ch == '\\' && inString -> escaped = true
                    ch == '"' -> inString = !inString
                    !inString -> {
                        if (ch == '{' || ch == '[') {
                            stack.addLast(index to ch)
                        } else if (ch == '}' || ch == ']') {
                            if (stack.isNotEmpty()) {
                                val (topIndex, topChar) = stack.last()
                                if ((topChar == '{' && ch == '}') || (topChar == '[' && ch == ']')) {
                                    stack.removeLast()
                                    if (index > topIndex) {
                                        foldRanges[topIndex] = index
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        var stringStart: Int? = null
        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (stringStart == null) {
                if (trimmed.startsWith("\"") && trimmed.contains(":")) {
                    val valPart = trimmed.substringAfter(":").trim()
                    if (valPart.startsWith("\"") && countQuotes(valPart) % 2 != 0) {
                        stringStart = index
                    }
                }
            } else {
                if (countQuotes(trimmed) % 2 != 0) {
                    foldRanges[stringStart] = index
                    stringStart = null
                }
            }
        }

        return foldRanges
    }

    override fun resolveClosingSymbol(lines: List<String>, endLineIndex: Int): String {
        if (endLineIndex < 0 || endLineIndex >= lines.size) return ""
        val endLineTrimmed = lines[endLineIndex].trim()
        return when {
            endLineTrimmed.startsWith("}") && endLineTrimmed.endsWith(",") -> "}, "
            endLineTrimmed.startsWith("}") -> "} "
            endLineTrimmed.startsWith("]") && endLineTrimmed.endsWith(",") -> "], "
            endLineTrimmed.startsWith("]") -> "] "
            else -> ""
        }
    }

    override fun highlightLine(lineText: String): androidx.compose.ui.text.AnnotatedString {
        return TokenMaker.tokenizeLine(
            lineText,
            TokenState.NULL
        ).annotatedString
    }

    private fun countQuotes(str: String): Int {
        var count = 0
        var escaped = false
        for (ch in str) {
            if (escaped) {
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else if (ch == '"') {
                count++
            }
        }
        return count
    }
}
