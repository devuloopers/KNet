package com.devuloopers.knet.engine.formatter.formatters

import com.devuloopers.knet.engine.formatter.BodyFormatter
import com.devuloopers.knet.engine.formatter.model.BodyFormat
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper

private val xmlMapper = XmlMapper().enable(SerializationFeature.INDENT_OUTPUT)

/**
 * High-performance, fault-tolerant XML payload formatter.
 * Handles application/xml, text/xml, application/soap+xml, application/rss+xml, and image/svg+xml.
 */
class XmlBodyFormatter : BodyFormatter {

    override val priority: Int = 18

    override fun matches(headers: Map<String, String>, bodyText: String): Boolean {
        val contentType = headers.entries.find { it.key.equals("content-type", ignoreCase = true) }?.value ?: ""
        if (contentType.lowercase().contains("xml")) return true
        val trimmed = bodyText.trim()
        return (trimmed.startsWith("<?xml") || (trimmed.startsWith("<") && trimmed.endsWith(">")))
    }

    override fun format(headers: Map<String, String>, bodyText: String): BodyFormat {
        val formattedText = prettyPrint(bodyText)
        return BodyFormat.Xml(formattedText)
    }

    /**
     * Formats an XML string with indentation using Jackson XmlMapper, falling back to a
     * manual indent formatter for malformed or truncated input.
     *
     * @param body Raw XML string.
     * @return Indented XML string.
     */
    fun prettyPrint(body: String): String = Companion.prettyPrint(body)

    companion object {
        /**
         * Formats an XML string with indentation using Jackson XmlMapper, falling back to a
         * manual indent formatter for malformed or truncated input.
         *
         * @param body Raw XML string.
         * @return Indented XML string.
         */
        fun prettyPrint(body: String): String {
            val trimmed = body.trim()
            if (trimmed.isEmpty()) return body

            return try {
                formatXmlString(trimmed)
            } catch (_: Exception) {
                formatXmlSoftFallback(trimmed)
            }
        }

        private fun formatXmlString(input: String): String {
            val node = xmlMapper.readTree(input)
            return xmlMapper.writeValueAsString(node).trim()
        }

        private fun formatXmlSoftFallback(input: String): String {
            val builder = StringBuilder()
            var indentLevel = 0
            val lines = input.replace("><", ">\n<").split("\n")

            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue

                if (line.startsWith("</")) {
                    indentLevel = (indentLevel - 1).coerceAtLeast(0)
                }

                builder.append("  ".repeat(indentLevel)).append(line).append("\n")

                if (line.startsWith("<") && !line.startsWith("</") && !line.startsWith("<?") && !line.startsWith("<!--") && !line.endsWith(
                        "/>"
                    ) && !line.contains("</")
                ) {
                    indentLevel++
                }
            }
            return builder.toString().trimEnd()
        }
    }
}
