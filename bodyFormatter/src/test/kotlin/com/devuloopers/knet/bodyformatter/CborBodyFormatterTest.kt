package com.devuloopers.knet.bodyformatter

import com.devuloopers.knet.bodyformatter.formatter.CborBodyFormatter
import com.devuloopers.knet.bodyformatter.model.BodyFormat
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CborBodyFormatterTest {
    private val formatter = CborBodyFormatter()
    private val cborMapper = CBORMapper()

    @Test
    fun testMatchesCborContentType() {
        assertTrue(formatter.matches(mapOf("Content-Type" to "application/cbor"), ""))
        assertTrue(formatter.matches(mapOf("content-type" to "application/cbor; charset=utf-8"), ""))
    }

    @Test
    fun testFormatValidCbor() {
        val testData = mapOf("id" to 42, "name" to "KNet")
        val cborBytes = cborMapper.writeValueAsBytes(testData)
        // Store binary bytes as ISO-8859-1 string as done in KNet proxy pipeline
        val bodyText = String(cborBytes, Charsets.ISO_8859_1)

        val formatResult = formatter.format(mapOf("content-type" to "application/cbor"), bodyText)
        assertTrue(formatResult is BodyFormat.Cbor)

        val formattedText = formatResult.formattedText
        assertTrue(formattedText.contains("42"))
        assertTrue(formattedText.contains("KNet"))
    }
}
