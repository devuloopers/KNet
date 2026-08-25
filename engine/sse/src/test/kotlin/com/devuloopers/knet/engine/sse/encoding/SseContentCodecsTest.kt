package com.devuloopers.knet.engine.sse.encoding

import com.devuloopers.knet.engine.sse.protocol.SseLimits
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SseContentCodecsTest {
    @Test
    fun `identity gzip deflate and raw deflate decode across every byte boundary`() {
        val original = "id: 7\nevent: price\ndata: first\n\n: ping\n\n".encodeToByteArray()
        val representations = listOf(
            null to original,
            "identity" to original,
            "gzip" to gzip(original),
            "deflate" to deflate(original, nowrap = false),
            "deflate" to deflate(original, nowrap = true),
        )

        representations.forEach { (encoding, representation) ->
            val decoded = decode(encoding, representation, chunkBytes = 1)
            assertContentEquals(original, decoded, "Failed content encoding $encoding")
        }
    }

    @Test
    fun `stacked coding is decoded in reverse declaration order`() {
        val original = "data: chained\n\n".encodeToByteArray()
        val representation = deflate(gzip(original), nowrap = false)

        assertContentEquals(original, decode("gzip, deflate", representation, chunkBytes = 3))
    }

    @Test
    fun `encoder and decoder round trip supported streaming codings`() {
        listOf("gzip", "deflate", "gzip, deflate").forEach { contentEncoding ->
            val plan = supportedPlan(contentEncoding)
            val encoder = plan.openEncoder()
            val encoded = buildList {
                add(assertIs<SseContentCodecResult.Output>(encoder.accept("data: ".encodeToByteArray(), false)).copyBytes())
                add(assertIs<SseContentCodecResult.Output>(encoder.accept("round trip\n\n".encodeToByteArray(), true)).copyBytes())
            }.concatenate()

            assertContentEquals("data: round trip\n\n".encodeToByteArray(), decode(contentEncoding, encoded, 2))
        }
    }

    @Test
    fun `malformed gzip and unsupported coding fail with stable reasons`() {
        val malformed = supportedPlan("gzip").openDecoder().accept(byteArrayOf(0x1f, 0x00), true)
        val unsupported = SseContentCodecRegistry(SseLimits()).resolve("br")

        assertEquals(
            SseContentCodecFailure.MALFORMED_STREAM,
            assertIs<SseContentCodecResult.Failure>(malformed).reason,
        )
        assertEquals(
            SseContentCodecFailure.UNSUPPORTED_ENCODING,
            assertIs<SseContentCodecPlanResult.Unavailable>(unsupported).reason,
        )
    }

    @Test
    fun `expansion and layer limits detach decoding predictably`() {
        val limits = SseLimits(
            maximumDecoderExpansionGraceBytes = 8,
            maximumDecoderExpansionRatio = 2,
            maximumContentEncodingLayers = 1,
        )
        val decoder = supportedPlan("gzip", limits).openDecoder()
        val expanded = decoder.accept(gzip(ByteArray(4_096) { 'a'.code.toByte() }), true)
        val layered = SseContentCodecRegistry(limits).resolve("gzip, deflate")

        assertEquals(
            SseContentCodecFailure.EXPANSION_LIMIT,
            assertIs<SseContentCodecResult.Failure>(expanded).reason,
        )
        assertEquals(
            SseContentCodecFailure.ENCODING_LAYER_LIMIT,
            assertIs<SseContentCodecPlanResult.Unavailable>(layered).reason,
        )
    }

    private fun decode(contentEncoding: String?, bytes: ByteArray, chunkBytes: Int): ByteArray {
        val decoder = supportedPlan(contentEncoding).openDecoder()
        val chunks = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(bytes.size, offset + chunkBytes)
            val result = decoder.accept(bytes.copyOfRange(offset, end), end == bytes.size)
            chunks += assertIs<SseContentCodecResult.Output>(result).copyBytes()
            offset = end
        }
        if (bytes.isEmpty()) {
            chunks += assertIs<SseContentCodecResult.Output>(decoder.accept(ByteArray(0), true)).copyBytes()
        }
        return chunks.concatenate()
    }

    private fun supportedPlan(
        contentEncoding: String?,
        limits: SseLimits = SseLimits(),
    ): SseContentCodecPlan = assertIs<SseContentCodecPlanResult.Supported>(
        SseContentCodecRegistry(limits).resolve(contentEncoding),
    ).plan

    private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().use { output ->
        GZIPOutputStream(output).use { gzip -> gzip.write(bytes) }
        output.toByteArray()
    }

    private fun deflate(bytes: ByteArray, nowrap: Boolean): ByteArray = ByteArrayOutputStream().use { output ->
        DeflaterOutputStream(output, Deflater(Deflater.DEFAULT_COMPRESSION, nowrap)).use { stream ->
            stream.write(bytes)
        }
        output.toByteArray()
    }
}

private fun List<ByteArray>.concatenate(): ByteArray {
    val output = ByteArray(sumOf(ByteArray::size))
    var offset = 0
    forEach { chunk ->
        chunk.copyInto(output, offset)
        offset += chunk.size
    }
    return output
}
