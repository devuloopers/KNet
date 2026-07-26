package com.devuloopers.knet.bodyformatter.formatter

import com.devuloopers.knet.bodyformatter.model.BodyFormat
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import org.msgpack.jackson.dataformat.MessagePackFactory

/**
 * Strategy formatter for MessagePack binary payloads.
 * Converts binary MessagePack to pretty-printed JSON representation for display.
 */
class MessagePackBodyFormatter : BodyFormatter {
    override val priority: Int = 19
    private val msgpackMapper = ObjectMapper(MessagePackFactory())
    private val jsonMapper = ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)

    override fun matches(headers: Map<String, String>, bodyText: String): Boolean {
        val contentType = headers.entries.find { it.key.equals("content-type", ignoreCase = true) }?.value ?: ""
        val lowerType = contentType.lowercase()
        return lowerType.contains("msgpack") || lowerType.contains("messagepack")
    }

    override fun format(headers: Map<String, String>, bodyText: String): BodyFormat {
        if (bodyText.isEmpty()) return BodyFormat.Json("{}")
        return try {
            val bytes = bodyText.toByteArray(Charsets.ISO_8859_1)
            val node = msgpackMapper.readTree(bytes)
            val prettyJson = jsonMapper.writeValueAsString(node)
            BodyFormat.Json(prettyJson)
        } catch (e: Exception) {
            BodyFormat.RawText(bodyText)
        }
    }
}
