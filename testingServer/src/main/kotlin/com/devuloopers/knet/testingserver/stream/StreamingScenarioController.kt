package com.devuloopers.knet.testingserver.stream

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import kotlin.time.Duration.Companion.milliseconds

/**
 * Structured body carried by each server-sent event.
 *
 * @property sequence One-based event order.
 * @property message Deterministic event content.
 */
data class StreamEvent(
    val sequence: Int,
    val message: String,
)

/** Supplies bounded streaming fixtures for SSE and ordinary chunked response capture. */
@RestController
@RequestMapping("/lab/v1/streams")
class StreamingScenarioController {
    /**
     * Emits a finite SSE stream with IDs, event names, and structured JSON data.
     *
     * @param count Event count clamped to one through one hundred.
     * @param delayMillis Delay between events, clamped to ten seconds.
     * @return Cold server-sent event stream that completes after the requested count.
     */
    @GetMapping("/sse", produces = ["text/event-stream"])
    fun serverSentEvents(
        @RequestParam(defaultValue = "5") count: Int,
        @RequestParam(defaultValue = "250") delayMillis: Long,
    ): Flow<ServerSentEvent<StreamEvent>> = flow {
        val effectiveCount = count.coerceIn(1, MAX_EVENTS)
        val effectiveDelay = delayMillis.coerceIn(0L, MAX_DELAY_MILLIS)
        repeat(effectiveCount) { index ->
            val sequence = index + 1
            emit(
                ServerSentEvent.builder(
                    StreamEvent(sequence = sequence, message = "sse-event-$sequence"),
                )
                    .id(sequence.toString())
                    .event("protocol-lab-event")
                    .build(),
            )
            if (effectiveDelay > 0L && sequence < effectiveCount) {
                delay(effectiveDelay.milliseconds)
            }
        }
    }

    /**
     * Emits plain-text chunks without SSE framing.
     *
     * @param count Chunk count clamped to one through one hundred.
     * @param delayMillis Delay between chunks, clamped to ten seconds.
     * @return Cold chunk stream ending with newline-delimited records.
     */
    @GetMapping("/chunks", produces = ["text/plain"])
    fun chunks(
        @RequestParam(defaultValue = "5") count: Int,
        @RequestParam(defaultValue = "250") delayMillis: Long,
    ): Flow<String> = flow {
        val effectiveCount = count.coerceIn(1, MAX_EVENTS)
        val effectiveDelay = delayMillis.coerceIn(0L, MAX_DELAY_MILLIS)
        repeat(effectiveCount) { index ->
            val sequence = index + 1
            emit("chunk-$sequence\n")
            if (effectiveDelay > 0L && sequence < effectiveCount) {
                delay(effectiveDelay.milliseconds)
            }
        }
    }

    private companion object {
        const val MAX_EVENTS = 100
        const val MAX_DELAY_MILLIS = 10_000L
    }
}
