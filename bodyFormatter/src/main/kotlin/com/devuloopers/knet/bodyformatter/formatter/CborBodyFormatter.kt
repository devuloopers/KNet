package com.devuloopers.knet.bodyformatter.formatter

import com.devuloopers.knet.bodyformatter.model.BodyFormat
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper

/**
 * Strategy formatter for Concise Binary Object Representation (CBOR) payloads.
 * Converts binary CBOR to pretty-printed JSON representation for display.
 */
class CborBodyFormatter : BodyFormatter {
    override val priority: Int = 19
    private val cborMapper = CBORMapper()
    private val jsonMapper = ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)

    override fun matches(headers: Map<String, String>, bodyText: String): Boolean {
        val contentType = headers.entries.find { it.key.equals("content-type", ignoreCase = true) }?.value ?: ""
        return contentType.lowercase().contains("cbor")
    }

    override fun format(headers: Map<String, String>, bodyText: String): BodyFormat {
        if (bodyText.isEmpty()) return BodyFormat.Json("{}")
        return try {
            // Retrieve raw bytes from the bodyText string representation (which is stored ISO-8859-1 encoded)
            val bytes = bodyText.toByteArray(Charsets.ISO_8859_1)
            val node = cborMapper.readTree(bytes)
            val prettyJson = jsonMapper.writeValueAsString(node)
            BodyFormat.Cbor(prettyJson)
        } catch (e: Exception) {
            // In case of parsing exception, fallback to raw text
            BodyFormat.RawText(bodyText)
        }
    }
}
