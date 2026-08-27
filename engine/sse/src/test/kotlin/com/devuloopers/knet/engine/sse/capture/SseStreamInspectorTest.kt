package com.devuloopers.knet.engine.sse.capture

import com.devuloopers.knet.engine.proxy.capture.ProxyBodyReservation
import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyMessageCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyMessageCaptureMetadata
import com.devuloopers.knet.engine.proxy.inspection.ProxyPayloadSlice
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.ExchangeTerminalOutcome
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficTerminationReason
import com.devuloopers.knet.traffic.model.body.ContentEncoding
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.Authority
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.HeaderName
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.HttpStatus
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.RequestTarget
import com.devuloopers.knet.traffic.model.http.ResponseHead
import com.devuloopers.knet.traffic.model.message.MessageProtocolId
import com.devuloopers.knet.traffic.model.message.ProtocolMessageKind
import com.devuloopers.knet.traffic.model.message.ProtocolMessageState
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class SseStreamInspectorTest {
    @Test
    fun `captures ordered records across arbitrary CRLF chunks`() {
        val capture = RecordingExchangeCapture()
        val inspector = requireNotNull(SseStreamInspectorFactory().create(request(), null, capture))
        inspector.onResponse(response(), 2L)

        listOf("id: 1\r", "\ndata: one\r\n\r", "\n: ping\ndata: two\n\n").forEachIndexed { index, text ->
            inspector.onPayload(TrafficDirection.SERVER_TO_CLIENT, Slice(text.encodeToByteArray()), 3L + index)
        }
        inspector.onDirectionEnd(TrafficDirection.SERVER_TO_CLIENT, 10L)

        assertEquals(2, capture.messages.size)
        assertEquals(listOf(1L, 2L), capture.messages.map { it.metadata.messageSequence })
        assertEquals(List(2) { MessageProtocolId.SSE }, capture.messages.map { it.metadata.protocol })
        assertEquals(List(2) { ProtocolMessageKind.RECORD }, capture.messages.map { it.metadata.kind })
        assertContentEquals("id: 1\r\ndata: one\r\n\r\n".encodeToByteArray(), capture.messages[0].payload())
        assertContentEquals(": ping\ndata: two\n\n".encodeToByteArray(), capture.messages[1].payload())
        assertEquals(List(2) { ProtocolMessageState.COMPLETE }, capture.messages.map { it.state })
    }

    @Test
    fun `unterminated record fails only semantic child capture`() {
        val capture = RecordingExchangeCapture()
        val inspector = requireNotNull(SseStreamInspectorFactory().create(request(), null, capture))
        inspector.onResponse(response(), 2L)
        inspector.onPayload(TrafficDirection.SERVER_TO_CLIENT, Slice("data: pending".encodeToByteArray()), 3L)
        inspector.onDirectionEnd(TrafficDirection.SERVER_TO_CLIENT, 4L)

        assertEquals(ProtocolMessageState.FAILED, capture.messages.single().state)
        assertEquals(
            "sse_record_ended_without_blank_line",
            capture.messages.single().terminationReason?.code?.value,
        )
    }

    @Test
    fun `gzip records are decoded incrementally before canonical child capture`() {
        val capture = RecordingExchangeCapture()
        val inspector = requireNotNull(SseStreamInspectorFactory().create(request(), null, capture))
        inspector.onResponse(response("gzip"), 2L)
        val compressed = gzip("event: price\ndata: 42\n\n".encodeToByteArray())

        compressed.forEachIndexed { index, byte ->
            inspector.onPayload(TrafficDirection.SERVER_TO_CLIENT, Slice(byteArrayOf(byte)), 3L + index)
        }
        inspector.onDirectionEnd(TrafficDirection.SERVER_TO_CLIENT, 100L)

        assertEquals(1, capture.messages.size)
        assertContentEquals("event: price\ndata: 42\n\n".encodeToByteArray(), capture.messages.single().payload())
        assertEquals(ProtocolMessageState.COMPLETE, capture.messages.single().state)
        assertEquals(false, capture.messages.single().metadata.compressed)
    }

    @Test
    fun `unsupported content encoding records one failure without parsing representation bytes`() {
        val capture = RecordingExchangeCapture()
        val inspector = requireNotNull(SseStreamInspectorFactory().create(request(), null, capture))
        inspector.onResponse(response("br"), 2L)

        inspector.onPayload(TrafficDirection.SERVER_TO_CLIENT, Slice(byteArrayOf(1, 2, 3)), 3L)
        inspector.onPayload(TrafficDirection.SERVER_TO_CLIENT, Slice(byteArrayOf(4)), 4L)

        assertEquals(1, capture.messages.size)
        assertEquals(ProtocolMessageState.FAILED, capture.messages.single().state)
        assertEquals("sse_content_encoding_unsupported", capture.messages.single().terminationReason?.code?.value)
    }

    private fun request(): RequestHead = RequestHead(
        method = HttpMethod.GET,
        target = RequestTarget.Absolute(HttpScheme.fromToken("https"), Authority("events.test"), "/events"),
        protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
        headers = emptyList(),
    )

    private fun response(contentEncoding: String? = null): ResponseHead = ResponseHead(
        protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
        status = HttpStatus(200),
        reasonPhrase = "OK",
        headers = buildList {
            add(HeaderField(HeaderName("Content-Type"), "text/event-stream; charset=utf-8"))
            contentEncoding?.let { add(HeaderField(HeaderName("Content-Encoding"), it)) }
        },
    )

    private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().use { output ->
        GZIPOutputStream(output).use { stream -> stream.write(bytes) }
        output.toByteArray()
    }
}

private class Slice(private val bytes: ByteArray) : ProxyPayloadSlice {
    override val size: Int = bytes.size

    override fun indexOf(value: Byte, startIndex: Int): Int =
        (startIndex until bytes.size).firstOrNull { bytes[it] == value } ?: -1

    override fun copyTo(destination: ByteArray, destinationOffset: Int, sourceOffset: Int, length: Int) {
        bytes.copyInto(destination, destinationOffset, sourceOffset, sourceOffset + length)
    }
}

private class RecordingExchangeCapture : ProxyExchangeCapture {
    override val exchangeId: ExchangeId = ExchangeId("sse-capture-test")
    val messages = mutableListOf<RecordingMessageCapture>()

    override fun startMessage(metadata: ProxyMessageCaptureMetadata): ProxyMessageCapture =
        RecordingMessageCapture(metadata).also(messages::add)

    override fun tryReserveBody(
        direction: TrafficDirection,
        contentEncoding: ContentEncoding?,
        requestedBytes: Int,
    ): ProxyBodyReservation? = null

    override fun completeBody(direction: TrafficDirection, observedBytes: Long, occurredAtEpochMillis: Long) = Unit
    override fun cancelBody(
        direction: TrafficDirection,
        observedBytes: Long,
        occurredAtEpochMillis: Long,
        reason: TrafficTerminationReason,
    ) = Unit
    override fun observeResponse(response: ResponseHead, occurredAtEpochMillis: Long) = Unit
    override fun terminate(
        outcome: ExchangeTerminalOutcome,
        timings: ExchangeTimings,
        occurredAtEpochMillis: Long,
    ) = Unit
}

private class RecordingMessageCapture(
    val metadata: ProxyMessageCaptureMetadata,
) : ProxyMessageCapture {
    override val messageId = metadata.messageId
    private val chunks = mutableListOf<ByteArray>()
    var state: ProtocolMessageState = ProtocolMessageState.IN_PROGRESS
    var terminationReason: TrafficTerminationReason? = null

    override fun tryReservePayload(requestedBytes: Int): ProxyBodyReservation = object : ProxyBodyReservation {
        override val writableBytes: ByteArray = ByteArray(requestedBytes)

        override fun publish(occurredAtEpochMillis: Long): Boolean {
            chunks += writableBytes.copyOf()
            return true
        }

        override fun cancel() = Unit
    }

    override fun complete(observedBytes: Long, occurredAtEpochMillis: Long) {
        state = ProtocolMessageState.COMPLETE
    }

    override fun terminate(
        observedBytes: Long,
        state: ProtocolMessageState,
        occurredAtEpochMillis: Long,
        reason: TrafficTerminationReason?,
    ) {
        this.state = state
        this.terminationReason = reason
    }

    fun payload(): ByteArray = chunks.fold(ByteArray(0)) { accumulated, chunk -> accumulated + chunk }
}
