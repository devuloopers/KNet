package com.devuloopers.knet.engine.formatter.formatters

import com.devuloopers.knet.engine.formatter.BodyFormatter
import com.devuloopers.knet.engine.formatter.model.BodyFormat
import com.devuloopers.knet.engine.formatter.util.appendCloseBrace
import com.devuloopers.knet.engine.formatter.util.appendOpenBrace

/**
 * Strategy formatter for Cascading Style Sheets (CSS).
 */
class CssBodyFormatter : BodyFormatter {
    override val priority: Int = 55

    override fun matches(headers: Map<String, String>, bodyText: String): Boolean {
        val contentType = headers.entries.find { it.key.equals("content-type", ignoreCase = true) }?.value ?: ""
        return contentType.lowercase().contains("css")
    }

    override fun format(headers: Map<String, String>, bodyText: String): BodyFormat {
        val formatted = prettyPrintCss(bodyText)
        return BodyFormat.Css(formatted)
    }

    /**
     * Pretty-prints a CSS stylesheet string with indentation and comment preservation.
     *
     * @param raw Raw CSS string.
     * @return Formatted CSS string.
     */
    fun prettyPrintCss(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return raw

        val builder = StringBuilder()
        var indentLevel = 0
        var inComment = false
        var i = 0

        while (i < trimmed.length) {
            val ch = trimmed[i]

            if (!inComment && i + 1 < trimmed.length && ch == '/' && trimmed[i + 1] == '*') {
                inComment = true
                if (builder.isNotEmpty() && builder.last() != '\n') builder.append('\n')
                builder.append("  ".repeat(indentLevel)).append("/*")
                i += 2
                continue
            }
            if (inComment && i + 1 < trimmed.length && ch == '*' && trimmed[i + 1] == '/') {
                inComment = false
                builder.append("*/\n")
                i += 2
                continue
            }

            if (inComment) {
                builder.append(ch)
                i++
                continue
            }

            when (ch) {
                '{' -> { indentLevel = builder.appendOpenBrace(indentLevel) }
                '}' -> { indentLevel = builder.appendCloseBrace(indentLevel, "\n\n") }
                ';' -> { builder.append(";\n").append("  ".repeat(indentLevel)) }
                ':' -> { builder.append(": ") }
                '\n', '\r', '\t', ' ' -> {
                    if (builder.isNotEmpty() && !builder.last().isWhitespace()) {
                        builder.append(' ')
                    }
                }
                else -> { builder.append(ch) }
            }
            i++
        }

        return builder.toString()
            .replace(Regex("(?m)^[ \t]*$"), "")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}
