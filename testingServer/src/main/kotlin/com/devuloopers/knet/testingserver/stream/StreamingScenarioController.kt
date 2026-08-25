package com.devuloopers.knet.testingserver.stream

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
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
    @GetMapping("/sse", "/sse/finite", produces = ["text/event-stream"])
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

    /** Emits a cancellable stream that remains open until the client disconnects. */
    @GetMapping("/sse/live", produces = ["text/event-stream"])
    fun liveServerSentEvents(
        @RequestParam(defaultValue = "250") delayMillis: Long,
    ): Flow<ServerSentEvent<StreamEvent>> = flow {
        val effectiveDelay = delayMillis.coerceIn(1L, MAX_DELAY_MILLIS)
        var sequence = 1
        while (true) {
            emit(event(sequence, "live-event-$sequence"))
            sequence += 1
            delay(effectiveDelay.milliseconds)
        }
    }

    /** Emits one event containing multiple data fields and therefore a newline-joined data value. */
    @GetMapping("/sse/multiline")
    fun multilineServerSentEvent(response: ServerHttpResponse): Mono<Void> = response.writeRawEventStream(
        listOf("id: multi-1\nevent: multiline\ndata: first\ndata: second\n\n".encodeToByteArray()),
    )

    /** Emits comment-only and ordinary records so keep-alive handling can be verified independently. */
    @GetMapping("/sse/comments")
    fun commentServerSentEvents(response: ServerHttpResponse): Mono<Void> = response.writeRawEventStream(
        listOf(": heartbeat\n\ndata: after-comment\n\n".encodeToByteArray()),
    )

    /** Writes one valid record across deliberately inconvenient transport chunks. */
    @GetMapping("/sse/fragmented")
    fun fragmentedServerSentEvent(response: ServerHttpResponse): Mono<Void> = response.writeRawEventStream(
        listOf("id: 7\nev".encodeToByteArray(), "ent: frag".encodeToByteArray(), "mented\ndata: pay".encodeToByteArray(),
            "load\n\n".encodeToByteArray()),
    )

    /** Emits malformed UTF-8 followed by a valid event to exercise explicit gap recovery. */
    @GetMapping("/sse/malformed")
    fun malformedServerSentEvent(response: ServerHttpResponse): Mono<Void> = response.writeRawEventStream(
        listOf(
            byteArrayOf(
                'd'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte(), ':'.code.toByte(),
                ' '.code.toByte(), 0xC3.toByte(), 0x28.toByte(), '\n'.code.toByte(), '\n'.code.toByte(),
            ),
            "id: recovered\ndata: valid-after-gap\n\n".encodeToByteArray(),
        ),
    )

    /** Closes after one complete event and one unterminated record, simulating a mid-record disconnect. */
    @GetMapping("/sse/disconnect")
    fun disconnectedServerSentEvent(response: ServerHttpResponse): Mono<Void> = response.writeRawEventStream(
        listOf("id: 1\ndata: delivered\n\nid: 2\ndata: incomplete".encodeToByteArray()),
    )

    /** Continues deterministic IDs after the caller-provided SSE reconnect cursor. */
    @GetMapping("/sse/resume", produces = ["text/event-stream"])
    fun resumedServerSentEvents(
        @RequestHeader(name = "Last-Event-ID", required = false) lastEventId: String?,
        @RequestParam(defaultValue = "3") count: Int,
    ): Flow<ServerSentEvent<StreamEvent>> = flow {
        val firstSequence = (lastEventId?.toIntOrNull()?.coerceAtLeast(0) ?: 0) + 1
        repeat(count.coerceIn(1, MAX_EVENTS)) { offset ->
            val sequence = firstSequence + offset
            emit(event(sequence, "resumed-event-$sequence"))
        }
    }

    /** Emits a gzip-encoded finite event stream for truthful unsupported-live-body classification tests. */
    @GetMapping("/sse/gzip")
    fun gzipServerSentEvents(response: ServerHttpResponse): Mono<Void> {
        response.headers[HttpHeaders.CONTENT_ENCODING] = "gzip"
        val encoded = gzip("id: gzip-1\nevent: compressed\ndata: gzip-event\n\n".encodeToByteArray())
        return response.writeRawEventStream(listOf(encoded))
    }

    /** Emits a large but bounded burst without delays for backpressure and retention tests. */
    @GetMapping("/sse/fast", produces = ["text/event-stream"])
    fun fastServerSentEvents(
        @RequestParam(defaultValue = "250") count: Int,
    ): Flow<ServerSentEvent<StreamEvent>> = flow {
        repeat(count.coerceIn(1, MAX_FAST_EVENTS)) { index ->
            val sequence = index + 1
            emit(event(sequence, "fast-event-$sequence"))
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
        const val MAX_FAST_EVENTS = 1_000
        const val MAX_DELAY_MILLIS = 10_000L

        fun event(sequence: Int, message: String): ServerSentEvent<StreamEvent> = ServerSentEvent.builder(
            StreamEvent(sequence = sequence, message = message),
        )
            .id(sequence.toString())
            .event("protocol-lab-event")
            .build()

        fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().use { output ->
            GZIPOutputStream(output).use { gzip -> gzip.write(bytes) }
            output.toByteArray()
        }
    }
}

private fun ServerHttpResponse.writeRawEventStream(chunks: List<ByteArray>): Mono<Void> {
    headers.contentType = MediaType.TEXT_EVENT_STREAM
    headers.cacheControl = "no-cache"
    val buffers: Flux<DataBuffer> = Flux.fromIterable(chunks.map(bufferFactory()::wrap))
    return writeWith(buffers)
}
