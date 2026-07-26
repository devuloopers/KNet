package com.devuloopers.knet.bodyformatter

import com.devuloopers.knet.bodyformatter.formatter.MessagePackBodyFormatter
import com.devuloopers.knet.bodyformatter.model.BodyFormat
import com.fasterxml.jackson.databind.ObjectMapper
import org.msgpack.jackson.dataformat.MessagePackFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessagePackBodyFormatterTest {
    private val formatter = MessagePackBodyFormatter()
    private val msgpackMapper = ObjectMapper(MessagePackFactory())

    @Test
    fun testMatchesMessagePackContentType() {
        assertTrue(formatter.matches(mapOf("Content-Type" to "application/x-msgpack"), ""))
        assertTrue(formatter.matches(mapOf("content-type" to "application/messagepack"), ""))
    }

    @Test
    fun testFormatValidMessagePack() {
        val testData = mapOf("status" to "success", "code" to 200)
        val msgpackBytes = msgpackMapper.writeValueAsBytes(testData)
        val bodyText = String(msgpackBytes, Charsets.ISO_8859_1)

        val formatResult = formatter.format(mapOf("content-type" to "application/x-msgpack"), bodyText)
        assertTrue(formatResult is BodyFormat.Json)

        val formattedText = formatResult.formattedText
        assertTrue(formattedText.contains("success"))
        assertTrue(formattedText.contains("200"))
    }
}
