package com.devuloopers.knet.bodyformatter.formatter

import com.devuloopers.knet.bodyformatter.model.BodyFormat
import java.io.StringReader
import java.io.StringWriter
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource

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

    fun prettyPrint(body: String): String {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return body

        return try {
            formatXmlString(trimmed)
        } catch (_: Exception) {
            // Soft fault-tolerant fallback if XML is malformed or truncated
            formatXmlSoftFallback(trimmed)
        }
    }

    private fun formatXmlString(input: String): String {
        val factory = TransformerFactory.newInstance()
        try {
            factory.setAttribute("indent-number", 2)
        } catch (_: Exception) {
            // Ignore if attribute not supported on specific JVM transformer providers
        }

        val transformer = factory.newTransformer()
        transformer.setOutputProperty(OutputKeys.INDENT, "yes")
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, if (input.startsWith("<?xml")) "no" else "yes")
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")

        val writer = StringWriter()
        transformer.transform(StreamSource(StringReader(input)), StreamResult(writer))
        return writer.toString().trim()
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

            if (line.startsWith("<") && !line.startsWith("</") && !line.startsWith("<?") && !line.startsWith("<!--") && !line.endsWith("/>") && !line.contains("</")) {
                indentLevel++
            }
        }
        return builder.toString().trimEnd()
    }
}
