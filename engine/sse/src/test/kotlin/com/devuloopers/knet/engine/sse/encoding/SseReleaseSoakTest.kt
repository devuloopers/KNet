package com.devuloopers.knet.engine.sse.encoding

import com.devuloopers.knet.engine.sse.protocol.SseLimits
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Configurable release-only SSE codec soak; the ordinary test task excludes this class. */
class SseReleaseSoakTest {
    @Test
    fun `encoded stream churn remains byte exact for the configured duration`() {
        val durationSeconds = System.getProperty("knet.sse.soak.seconds")
            ?.toLongOrNull()
            ?.coerceIn(MINIMUM_SECONDS, MAXIMUM_SECONDS)
            ?: DEFAULT_SECONDS
        val mark = TimeSource.Monotonic.markNow()
        var completedStreams = 0L
        while (mark.elapsedNow() < durationSeconds.seconds) {
            val contentEncoding = ENCODINGS[(completedStreams % ENCODINGS.size).toInt()]
            val input = "id: $completedStreams\nevent: soak\ndata: ${completedStreams.toString(16)}\n\n"
                .encodeToByteArray()
            val plan = assertIs<SseContentCodecPlanResult.Supported>(
                SseContentCodecRegistry(SseLimits()).resolve(contentEncoding),
            ).plan
            assertContentEquals(input, decodeStress(plan, encodeStress(plan, input, 7), 5))
            completedStreams++
        }
        check(completedStreams > 0L) { "The SSE release soak did not complete a stream." }
        println("KNET_SSE_SOAK durationSeconds=$durationSeconds completedStreams=$completedStreams")
    }

    private companion object {
        const val MINIMUM_SECONDS: Long = 1L
        const val DEFAULT_SECONDS: Long = 10_800L
        const val MAXIMUM_SECONDS: Long = 86_400L
        val ENCODINGS: List<String> = listOf("gzip", "deflate", "gzip, deflate")
    }
}
