package com.devuloopers.knet.bodyformatter.formatter

import com.devuloopers.knet.bodyformatter.model.BodyFormat

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

    fun prettyPrintCss(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return raw

        val builder = StringBuilder()
        var indentLevel = 0
        var inComment = false
        var i = 0

        while (i < trimmed.length) {
            val ch = trimmed[i]

            // Handle comments
            if (!inComment && i + 1 < trimmed.length && ch == '/' && trimmed[i + 1] == '*') {
                inComment = true
                if (builder.isNotEmpty() && builder.last() != '\n') {
                    builder.append('\n')
                }
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

            // Normal formatting
            when (ch) {
                '{' -> {
                    indentLevel = builder.appendOpenBrace(indentLevel)
                }
                '}' -> {
                    indentLevel = builder.appendCloseBrace(indentLevel, "\n\n")
                }
                ';' -> {
                    builder.append(";\n").append("  ".repeat(indentLevel))
                }
                ':' -> {
                    builder.append(": ")
                }
                '\n', '\r', '\t', ' ' -> {
                    // Normalize spacing - collapse contiguous whitespaces outside of values
                    if (builder.isNotEmpty() && !builder.last().isWhitespace()) {
                        builder.append(' ')
                    }
                }
                else -> {
                    builder.append(ch)
                }
            }
            i++
        }

        // Final formatting cleanup
        return builder.toString()
            .replace(Regex("(?m)^[ \t]*$"), "") // clear lines containing only whitespace
            .replace(Regex("\n{3,}"), "\n\n")   // collapse excess newlines
            .trim()
    }
}
