package com.devuloopers.knet.domain.clientNetwork.decoder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BodyDecoderTest {

    @Test
    fun testIdentityEncodingPassthrough() {
        val raw = "Hello KNet Network Inspector".encodeToByteArray()
        val headers = listOf("Content-Type" to "text/plain")

        val result = BodyDecoder.decode(raw, headers)
        assertTrue(result is DecodedBodyResult.Identity)
        assertEquals("Hello KNet Network Inspector", result.bytes.decodeToString())

        val textResult = BodyTextDecoder.decode(result, headers)
        assertTrue(textResult is DecodedTextResult.Success)
        assertEquals("Hello KNet Network Inspector", textResult.text)
        assertEquals(ContentEncoding.IDENTITY, textResult.encoding)
    }

    @Test
    fun testOHttpReqMediaCategoryDetection() {
        val raw = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05)
        val headers = listOf("Content-Type" to "message/ohttp-req")

        val result = BodyDecoder.decode(raw, headers)
        assertTrue(result is DecodedBodyResult.Identity)

        val textResult = BodyTextDecoder.decode(result, headers)
        assertTrue(textResult is DecodedTextResult.BinaryKnownType)
        assertEquals(BinaryCategory.OHTTP, textResult.category)
        assertEquals(6L, textResult.size)
    }

    @Test
    fun testApplicationOctetStreamBinaryCategoryDetection() {
        val raw = byteArrayOf(0x10, 0x20, 0x30)
        val headers = listOf("Content-Type" to "application/octet-stream")

        val result = BodyDecoder.decode(raw, headers)
        val textResult = BodyTextDecoder.decode(result, headers)
        assertTrue(textResult is DecodedTextResult.BinaryUnknownType)
        assertEquals(3L, textResult.size)
    }

    @Test
    fun testByteHeuristicNullByteBinaryDetection() {
        val rawWithNullByte = byteArrayOf(0x48, 0x65, 0x6C, 0x6C, 0x6F, 0x00, 0x57, 0x6F, 0x72, 0x6C, 0x64)
        val headers = emptyList<Pair<String, String>>()

        val result = BodyDecoder.decode(rawWithNullByte, headers)
        val textResult = BodyTextDecoder.decode(result, headers)
        assertTrue(textResult is DecodedTextResult.BinaryUnknownType)
        assertEquals(11L, textResult.size)
    }

    @Test
    fun testEmptyBodyHandling() {
        val emptyBytes = byteArrayOf()
        val headers = listOf("Content-Type" to "application/json")

        val result = BodyDecoder.decode(emptyBytes, headers)
        val textResult = BodyTextDecoder.decode(result, headers)
        assertTrue(textResult is DecodedTextResult.Success)
        assertEquals("", textResult.text)
    }

    @Test
    fun testUnsupportedEncodingSurfacesUnsupportedResult() {
        val raw = "Some Brotli payload".encodeToByteArray()
        val headers = listOf("Content-Encoding" to "custom-algorithm")

        val result = BodyDecoder.decode(raw, headers)
        assertTrue(result is DecodedBodyResult.UnsupportedEncoding)
        assertEquals("custom-algorithm", result.encoding)

        val textResult = BodyTextDecoder.decode(result, headers)
        assertTrue(textResult is DecodedTextResult.UnsupportedEncoding)
        assertEquals("custom-algorithm", textResult.encoding)
        assertEquals(raw.size.toLong(), textResult.size)
    }

    @Test
    fun testCorruptedGzipPayloadSurfacesCorruptedResult() {
        val corruptedBytes = byteArrayOf(0x1F, 0x8B.toByte(), 0x00, 0x12, 0x34) // Truncated GZIP header
        val headers = listOf("Content-Encoding" to "gzip")

        val result = BodyDecoder.decode(corruptedBytes, headers)
        assertTrue(result is DecodedBodyResult.CorruptedEncoding)
        assertEquals("gzip", result.encoding)

        val textResult = BodyTextDecoder.decode(result, headers)
        assertTrue(textResult is DecodedTextResult.DecodingError)
        assertEquals("gzip", textResult.encoding)
    }
}
