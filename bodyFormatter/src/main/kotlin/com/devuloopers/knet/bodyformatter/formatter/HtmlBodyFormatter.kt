package com.devuloopers.knet.bodyformatter.formatter

import com.devuloopers.knet.bodyformatter.model.BodyFormat

/**
 * Strategy for detecting and pretty-printing HTML and XML payloads.
 */
class HtmlBodyFormatter : BodyFormatter {
    override val priority: Int = 85

    override fun matches(headers: Map<String, String>, bodyText: String): Boolean {
        val contentType = headers.entries.find { it.key.equals("content-type", ignoreCase = true) }?.value ?: ""
        if (contentType.contains("html", ignoreCase = true) || contentType.contains("xml", ignoreCase = true)) {
            return true
        }
        val trimmed = bodyText.trim()
        if (trimmed.isEmpty()) return false
        return (trimmed.startsWith("<") && (
            trimmed.contains("html", ignoreCase = true) ||
            trimmed.contains("body", ignoreCase = true) ||
            trimmed.contains("head", ignoreCase = true) ||
            trimmed.contains("xml", ignoreCase = true) ||
            trimmed.startsWith("<!DOCTYPE", ignoreCase = true) ||
            trimmed.startsWith("<?xml", ignoreCase = true)
        ))
    }

    override fun format(headers: Map<String, String>, bodyText: String): BodyFormat {
        val pretty = prettyPrintHtml(bodyText)
        val contentType = headers.entries.find { it.key.equals("content-type", ignoreCase = true) }?.value ?: ""
        val isXml = contentType.contains("xml", ignoreCase = true) || bodyText.trim().startsWith("<?xml", ignoreCase = true)
        return if (isXml) BodyFormat.Xml(pretty) else BodyFormat.Html(pretty)
    }

    companion object {
        /**
         * Pretty prints unformatted HTML/XML text into indented tree structure.
         */
        fun prettyPrintHtml(input: String): String {
            if (input.isBlank()) return input
            val sb = StringBuilder()
            var indentLevel = 0
            val indentStr = "  "
            var i = 0

            val voidTags = setOf(
                "area", "base", "br", "col", "embed", "hr", "img", "input",
                "link", "meta", "param", "source", "track", "wbr"
            )

            while (i < input.length) {
                if (input[i] == '<') {
                    val endTagPos = input.indexOf('>', i)
                    if (endTagPos == -1) {
                        sb.append(input.substring(i))
                        break
                    }
                    val fullTag = input.substring(i, endTagPos + 1)
                    val tagContent = input.substring(i + 1, endTagPos).trim()

                    val isClosing = tagContent.startsWith("/")
                    val tagName = tagContent.removePrefix("/").substringBefore(" ").substringBefore(">").lowercase()
                    val isSelfClosing = tagContent.endsWith("/") || voidTags.contains(tagName)
                    val isCommentOrDoctype = tagContent.startsWith("!") || tagContent.startsWith("?")

                    if (isClosing && indentLevel > 0) {
                        indentLevel--
                    }

                    if (sb.isNotEmpty() && !sb.endsWith("\n")) {
                        sb.append("\n")
                    }
                    repeat(indentLevel) { sb.append(indentStr) }
                    sb.append(fullTag)

                    if (!isClosing && !isSelfClosing && !isCommentOrDoctype) {
                        indentLevel++
                    }

                    i = endTagPos + 1
                } else {
                    val nextTagPos = input.indexOf('<', i)
                    val text = if (nextTagPos == -1) input.substring(i) else input.substring(i, nextTagPos)
                    val trimmedText = text.trim()
                    if (trimmedText.isNotEmpty()) {
                        if (sb.isNotEmpty() && !sb.endsWith("\n")) {
                            sb.append("\n")
                        }
                        repeat(indentLevel) { sb.append(indentStr) }
                        sb.append(trimmedText)
                    }
                    i = if (nextTagPos == -1) input.length else nextTagPos
                }
            }

            return sb.toString().trim()
        }
    }
}
