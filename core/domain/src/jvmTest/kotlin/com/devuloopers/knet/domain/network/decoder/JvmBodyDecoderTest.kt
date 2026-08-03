package com.devuloopers.knet.domain.network.decoder

import com.github.luben.zstd.Zstd
import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JvmBodyDecoderTest {

    @Test
    fun testGzipDecompressionSuccess() {
        val originalText = "{\"service\":\"KNet\",\"status\":\"GZIP_DECOMPRESSED\"}"
        val compressedBytes = compressGzip(originalText.toByteArray(Charsets.UTF_8))
        val headers = listOf("Content-Encoding" to "gzip", "Content-Type" to "application/json")

        val result = BodyDecoder.decode(compressedBytes, headers)
        assertTrue(result is DecodedBodyResult.Success)
        assertEquals(ContentEncoding.GZIP, result.encoding)
        assertEquals(originalText, result.bytes.decodeToString())

        val textResult = BodyTextDecoder.decode(result, headers)
        assertTrue(textResult is DecodedTextResult.Success)
        assertEquals(originalText, textResult.text)
        assertEquals(ContentEncoding.GZIP, textResult.encoding)
    }

    @Test
    fun testDeflateDecompressionSuccess() {
        val originalText = "{\"service\":\"KNet\",\"status\":\"DEFLATE_DECOMPRESSED\"}"
        val compressedBytes = compressDeflate(originalText.toByteArray(Charsets.UTF_8))
        val headers = listOf("Content-Encoding" to "deflate", "Content-Type" to "application/json")

        val result = BodyDecoder.decode(compressedBytes, headers)
        assertTrue(result is DecodedBodyResult.Success)
        assertEquals(ContentEncoding.DEFLATE, result.encoding)
        assertEquals(originalText, result.bytes.decodeToString())
    }

    @Test
    fun testZstdDecompressionSuccess() {
        val originalText = "{\"service\":\"KNet\",\"status\":\"ZSTD_DECOMPRESSED\"}"
        val compressedBytes = Zstd.compress(originalText.toByteArray(Charsets.UTF_8))
        val headers = listOf("Content-Encoding" to "zstd", "Content-Type" to "application/json")

        val result = BodyDecoder.decode(compressedBytes, headers)
        assertTrue(result is DecodedBodyResult.Success)
        assertEquals(ContentEncoding.ZSTD, result.encoding)
        assertEquals(originalText, result.bytes.decodeToString())

        val textResult = BodyTextDecoder.decode(result, headers)
        assertTrue(textResult is DecodedTextResult.Success)
        assertEquals(originalText, textResult.text)
        assertEquals(ContentEncoding.ZSTD, textResult.encoding)
    }

    @Test
    fun testChainedEncodingsGzipDeflate() {
        val originalText = "{\"pipeline\":\"ChainedEncodingTest\"}"
        val gzipBytes = compressGzip(originalText.toByteArray(Charsets.UTF_8))
        val chainedBytes = compressDeflate(gzipBytes)
        val headers = listOf("Content-Encoding" to "gzip, deflate", "Content-Type" to "application/json")

        val result = BodyDecoder.decode(chainedBytes, headers)
        assertTrue(result is DecodedBodyResult.Success)
        assertEquals(originalText, result.bytes.decodeToString())

        val textResult = BodyTextDecoder.decode(result, headers)
        assertTrue(textResult is DecodedTextResult.Success)
        assertEquals(originalText, textResult.text)
    }

    private fun compressGzip(input: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { gzos ->
            gzos.write(input)
        }
        return baos.toByteArray()
    }

    private fun compressDeflate(input: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        DeflaterOutputStream(baos).use { dos ->
            dos.write(input)
        }
        return baos.toByteArray()
    }
}
