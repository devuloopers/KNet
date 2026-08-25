package com.devuloopers.knet.engine.sse.encoding

import com.devuloopers.knet.engine.sse.protocol.SseLimits
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertIs

/** Short deterministic churn coverage kept in the ordinary SSE qualification gate. */
class SseCodecStressTest {
    @Test
    fun `many independently chunked encoded streams preserve bytes and session isolation`() {
        val random = Random(0x5EED)
        repeat(STREAMS) { streamIndex ->
            val contentEncoding = ENCODINGS[streamIndex % ENCODINGS.size]
            val input = buildString {
                repeat(RECORDS_PER_STREAM) { recordIndex ->
                    append("id: ").append(streamIndex).append('-').append(recordIndex).append('\n')
                    append("event: stress\n")
                    append("data: ").append("payload-${random.nextInt()}").append("\n\n")
                }
            }.encodeToByteArray()
            val plan = assertIs<SseContentCodecPlanResult.Supported>(
                SseContentCodecRegistry(SseLimits()).resolve(contentEncoding),
            ).plan

            val encoded = encodeStress(plan, input, (streamIndex % 31) + 1)
            val decoded = decodeStress(plan, encoded, (streamIndex % 17) + 1)

            assertContentEquals(input, decoded, "Stream $streamIndex failed for $contentEncoding")
        }
    }

    @Test
    fun `closing partial sessions repeatedly releases their state without cross session contamination`() {
        repeat(PARTIAL_SESSIONS) { index ->
            val plan = assertIs<SseContentCodecPlanResult.Supported>(
                SseContentCodecRegistry(SseLimits()).resolve(ENCODINGS[index % ENCODINGS.size]),
            ).plan
            plan.openEncoder().also { encoder ->
                assertIs<SseContentCodecResult.Output>(encoder.accept("data: partial".encodeToByteArray(), false))
                encoder.close()
                encoder.close()
            }
            plan.openDecoder().also { decoder ->
                decoder.accept(byteArrayOf(0x1f), false)
                decoder.close()
                decoder.close()
            }
        }
    }

    private companion object {
        const val STREAMS: Int = 500
        const val RECORDS_PER_STREAM: Int = 12
        const val PARTIAL_SESSIONS: Int = 2_000
        val ENCODINGS: List<String> = listOf("gzip", "deflate", "gzip, deflate")
    }
}

internal fun encodeStress(plan: SseContentCodecPlan, input: ByteArray, chunkBytes: Int): ByteArray {
    val encoder = plan.openEncoder()
    return try {
        transformStress(input, chunkBytes, encoder::accept)
    } finally {
        encoder.close()
    }
}

internal fun decodeStress(plan: SseContentCodecPlan, input: ByteArray, chunkBytes: Int): ByteArray {
    val decoder = plan.openDecoder()
    return try {
        transformStress(input, chunkBytes, decoder::accept)
    } finally {
        decoder.close()
    }
}

private fun transformStress(
    input: ByteArray,
    chunkBytes: Int,
    operation: (ByteArray, Boolean) -> SseContentCodecResult,
): ByteArray {
    val output = mutableListOf<ByteArray>()
    var offset = 0
    while (offset < input.size) {
        val end = minOf(input.size, offset + chunkBytes)
        output += assertIs<SseContentCodecResult.Output>(
            operation(input.copyOfRange(offset, end), end == input.size),
        ).copyBytes()
        offset = end
    }
    if (input.isEmpty()) {
        output += assertIs<SseContentCodecResult.Output>(operation(ByteArray(0), true)).copyBytes()
    }
    return output.concatenateStressChunks()
}

private fun List<ByteArray>.concatenateStressChunks(): ByteArray {
    val output = ByteArray(sumOf(ByteArray::size))
    var offset = 0
    forEach { chunk ->
        chunk.copyInto(output, offset)
        offset += chunk.size
    }
    return output
}
