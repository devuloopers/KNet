package com.devuloopers.knet.ui.desktop.codeeditor

import androidx.compose.ui.text.AnnotatedString
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.FoldManager
import com.devuloopers.knet.ui.desktop.codeeditor.api.LARGE_PAYLOAD_LINE_THRESHOLD
import com.devuloopers.knet.ui.desktop.codeeditor.api.ProcessedPayloadState
import com.devuloopers.knet.ui.desktop.codeeditor.syntax.tokenizer.FsmTokenMakerVisualTransformation
import com.devuloopers.knet.ui.desktop.codeeditor.syntax.tokenizer.TokenMaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EditorPerformanceBenchmarkTest {

    private fun generateSyntheticJsonPayload(lineCount: Int): String {
        val builder = StringBuilder()
        builder.append("{\n  \"status\": \"success\",\n  \"items\": [\n")
        for (i in 1..lineCount) {
            builder.append("    { \"id\": $i, \"name\": \"Item_$i\", \"active\": true, \"value\": $i.5 }")
            if (i < lineCount) builder.append(",")
            builder.append("\n")
        }
        builder.append("  ]\n}")
        return builder.toString()
    }

    @Test
    fun testLruTokenCachePerformanceHitVsMiss() {
        val payload = generateSyntheticJsonPayload(2000)
        val transformation = FsmTokenMakerVisualTransformation(maxCacheEntries = 16)
        val annotatedText = AnnotatedString(payload)

        // Warmup pass
        transformation.filter(annotatedText)

        // Cold Pass
        val newPayloadText = AnnotatedString(payload + " ")
        val coldTimeNanos = measureNanoTime {
            transformation.filter(newPayloadText)
        }

        // Warm Pass
        var warmTimeNanosSum = 0L
        val scrollFramesCount = 100
        repeat(scrollFramesCount) {
            warmTimeNanosSum += measureNanoTime {
                val result = transformation.filter(newPayloadText)
                assertNotNull(result)
            }
        }
        val avgWarmTimeMs = (warmTimeNanosSum.toDouble() / scrollFramesCount) / 1_000_000.0
        val coldTimeMs = coldTimeNanos.toDouble() / 1_000_000.0

        assertTrue(avgWarmTimeMs < 1.0, "Warm LRU cache hit should execute in under 1.0ms (was $avgWarmTimeMs ms)")
    }

    @Test
    fun testZeroAllocationWhitespaceCheck() {
        val testLine = "    \"user_profile_id_key\":     \"value_12345\""
        val nextQuote = 25
        val colonIndex = 29
        val iterations = 100_000

        repeat(1000) {
            TokenMaker.isOnlyWhitespaceBetween(testLine, nextQuote + 1, colonIndex)
        }

        val zeroAllocTimeNanos = measureNanoTime {
            repeat(iterations) {
                val isSpace = TokenMaker.isOnlyWhitespaceBetween(testLine, nextQuote + 1, colonIndex)
                assertTrue(isSpace)
            }
        }

        val zeroMs = zeroAllocTimeNanos.toDouble() / 1_000_000.0
        assertTrue(zeroMs < 50.0, "Zero-allocation inline check should execute in < 50ms")
    }

    @Test
    fun testMemoizedFoldCalculationPerformance() {
        val lines = generateSyntheticJsonPayload(1000).lines()
        FoldManager.clearCache()

        val coldNanos = measureNanoTime {
            val folds = FoldManager.calculateFolds(lines)
            assertTrue(folds.isNotEmpty())
        }

        val warmNanos = measureNanoTime {
            val folds = FoldManager.calculateFolds(lines)
            assertTrue(folds.isNotEmpty())
        }

        val warmMs = warmNanos.toDouble() / 1_000_000.0
        assertTrue(warmMs < 2.0, "Memoized fold lookup should execute in < 2.0 ms")
    }

    @Test
    fun test100kLinesCoroutineBackgroundProcessing() = runBlocking {
        val payload100k = generateSyntheticJsonPayload(100000)

        lateinit var state: ProcessedPayloadState
        val executionNanos = measureNanoTime {
            state = withContext(Dispatchers.Default) {
                val lines = payload100k.lines()
                val total = lines.size
                val isTruncated = total > LARGE_PAYLOAD_LINE_THRESHOLD
                val previewText = if (isTruncated) lines.take(LARGE_PAYLOAD_LINE_THRESHOLD).joinToString("\n") else payload100k
                ProcessedPayloadState(
                    displayedText = previewText,
                    totalLineCount = total,
                    displayedLineCount = if (isTruncated) LARGE_PAYLOAD_LINE_THRESHOLD else total,
                    isTruncated = isTruncated
                )
            }
        }
        val executionMs = executionNanos.toDouble() / 1_000_000.0

        assertTrue(state.isTruncated, "100,000 line payload should activate truncation windowing")
        assertEquals(LARGE_PAYLOAD_LINE_THRESHOLD, state.displayedLineCount)
        assertTrue(executionMs < 500.0, "100,000 line coroutine truncation should execute in < 500 ms")
    }
}
