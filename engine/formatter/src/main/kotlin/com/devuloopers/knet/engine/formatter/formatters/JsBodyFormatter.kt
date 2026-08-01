package com.devuloopers.knet.engine.formatter.formatters

import com.devuloopers.knet.engine.formatter.BodyFormatter
import com.devuloopers.knet.engine.formatter.model.BodyFormat
import com.devuloopers.knet.engine.formatter.util.appendCloseBrace
import com.devuloopers.knet.engine.formatter.util.appendOpenBrace

/**
 * Strategy formatter for JavaScript (JS) source files.
 */
class JsBodyFormatter : BodyFormatter {
    override val priority: Int = 55

    override fun matches(headers: Map<String, String>, bodyText: String): Boolean {
        val contentType = headers.entries.find { it.key.equals("content-type", ignoreCase = true) }?.value ?: ""
        return contentType.lowercase().contains("javascript")
    }

    override fun format(headers: Map<String, String>, bodyText: String): BodyFormat {
        val formatted = prettyPrintJs(bodyText)
        return BodyFormat.Js(formatted)
    }

    /**
     * Pretty-prints JavaScript source code with indentation, string literal preservation,
     * and comment handling.
     *
     * @param raw Raw JavaScript string.
     * @return Formatted JavaScript string.
     */
    fun prettyPrintJs(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return raw

        val builder = StringBuilder()
        var indentLevel = 0
        var i = 0

        var inSingleQuote = false
        var inDoubleQuote = false
        var inTemplateLiteral = false
        var inLineComment = false
        var inBlockComment = false
        var escaped = false

        while (i < trimmed.length) {
            val ch = trimmed[i]

            if (escaped) {
                builder.append(ch)
                escaped = false
                i++
                continue
            }

            when {
                inLineComment -> {
                    builder.append(ch)
                    if (ch == '\n' || ch == '\r') {
                        inLineComment = false
                        builder.append("  ".repeat(indentLevel))
                    }
                    i++
                    continue
                }

                inBlockComment -> {
                    builder.append(ch)
                    if (ch == '/' && i > 0 && trimmed[i - 1] == '*') {
                        inBlockComment = false
                        builder.append('\n').append("  ".repeat(indentLevel))
                    }
                    i++
                    continue
                }

                inSingleQuote -> {
                    builder.append(ch)
                    if (ch == '\\') escaped = true
                    else if (ch == '\'') inSingleQuote = false
                    i++
                    continue
                }

                inDoubleQuote -> {
                    builder.append(ch)
                    if (ch == '\\') escaped = true
                    else if (ch == '"') inDoubleQuote = false
                    i++
                    continue
                }

                inTemplateLiteral -> {
                    builder.append(ch)
                    if (ch == '\\') escaped = true
                    else if (ch == '`') inTemplateLiteral = false
                    i++
                    continue
                }
            }

            if (ch == '/' && i + 1 < trimmed.length) {
                if (trimmed[i + 1] == '/') {
                    inLineComment = true; builder.append("//"); i += 2; continue
                } else if (trimmed[i + 1] == '*') {
                    inBlockComment = true; builder.append("/*"); i += 2; continue
                }
            }

            when (ch) {
                '\'' -> {
                    inSingleQuote = true; builder.append(ch); i++; continue
                }

                '"' -> {
                    inDoubleQuote = true; builder.append(ch); i++; continue
                }

                '`' -> {
                    inTemplateLiteral = true; builder.append(ch); i++; continue
                }
            }

            when (ch) {
                '{' -> {
                    indentLevel = builder.appendOpenBrace(indentLevel)
                }

                '}' -> {
                    indentLevel = builder.appendCloseBrace(indentLevel, "\n")
                    builder.append("  ".repeat(indentLevel))
                }

                ';' -> {
                    builder.append(";\n").append("  ".repeat(indentLevel))
                }

                '\n', '\r', '\t', ' ' -> {
                    if (builder.isNotEmpty() && !builder.last().isWhitespace() && builder.last() != ';') {
                        builder.append(' ')
                    }
                }

                else -> {
                    builder.append(ch)
                }
            }
            i++
        }

        return builder.toString()
            .replace(Regex("(?m)^[ \t]*$"), "")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}
