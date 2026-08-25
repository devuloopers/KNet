package com.devuloopers.knet.engine.grpc

import com.devuloopers.knet.engine.proxy.capture.ProxyBodyReservation
import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyMessageCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyMessageCaptureMetadata
import com.devuloopers.knet.engine.proxy.inspection.ProxyPayloadSlice
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.ProtocolMessageId
import com.devuloopers.knet.traffic.id.StreamId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.body.ContentEncoding
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.HeaderName
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.HttpStatus
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.RequestTarget
import com.devuloopers.knet.traffic.model.http.ResponseHead
import com.devuloopers.knet.traffic.model.http.StandardApplicationProtocol
import com.devuloopers.knet.traffic.model.http.StandardHttpScheme
import com.devuloopers.knet.traffic.model.message.ProtocolMessageState
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GrpcStreamInspectorTest {
    @Test
    fun `split envelopes and multiple messages preserve directional order and payloads`() {
        val capture = RecordingExchangeCapture()
        val inspector = assertNotNull(factory().create(request(), StreamId(3), capture))
        val wire = frame("first".encodeToByteArray()) + frame("second".encodeToByteArray())

        inspector.onPayload(TrafficDirection.CLIENT_TO_SERVER, Slice(wire.copyOfRange(0, 2)), 1L)
        inspector.onPayload(TrafficDirection.CLIENT_TO_SERVER, Slice(wire.copyOfRange(2, 11)), 2L)
        inspector.onPayload(TrafficDirection.CLIENT_TO_SERVER, Slice(wire.copyOfRange(11, wire.size)), 3L)
        inspector.onDirectionEnd(TrafficDirection.CLIENT_TO_SERVER, 4L)

        assertEquals(listOf(1L, 2L), capture.messages.map { it.metadata.messageSequence })
        assertEquals(listOf(ProtocolMessageState.COMPLETE, ProtocolMessageState.COMPLETE), capture.messages.map { it.state })
        assertContentEquals("first".encodeToByteArray(), capture.messages[0].payload())
        assertContentEquals("second".encodeToByteArray(), capture.messages[1].payload())
    }

    @Test
    fun `response is ignored until native grpc headers are observed`() {
        val capture = RecordingExchangeCapture()
        val inspector = assertNotNull(factory().create(request(), StreamId(5), capture))
        inspector.onPayload(TrafficDirection.SERVER_TO_CLIENT, Slice(frame(byteArrayOf(1))), 1L)
        assertEquals(0, capture.messages.size)

        inspector.onResponse(
            ResponseHead(
                protocol = ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_2),
                status = HttpStatus(200),
                headers = listOf(header("content-type", "application/grpc+proto")),
            ),
            2L,
        )
        inspector.onPayload(TrafficDirection.SERVER_TO_CLIENT, Slice(frame(byteArrayOf(2))), 3L)

        assertEquals(1, capture.messages.size)
        assertEquals(TrafficDirection.SERVER_TO_CLIENT, capture.messages.single().metadata.direction)
        assertContentEquals(byteArrayOf(2), capture.messages.single().payload())
    }

    @Test
    fun `invalid compression flag fails one message without consuming another frame`() {
        val capture = RecordingExchangeCapture()
        val inspector = assertNotNull(factory().create(request(), StreamId(7), capture))
        inspector.onPayload(
            TrafficDirection.CLIENT_TO_SERVER,
            Slice(frame(byteArrayOf(1, 2), compressionFlag = 2)),
            1L,
        )

        val message = capture.messages.single()
        assertEquals(ProtocolMessageState.FAILED, message.state)
        assertEquals("grpc_invalid_compression_flag", message.errorCode)
        assertEquals(0, message.observedBytes)
    }

    @Test
    fun `factory rejects ordinary HTTP2 and malformed grpc paths`() {
        assertNull(factory().create(request(contentType = "application/json"), StreamId(1), null))
        assertNull(factory().create(request(path = "/only-service"), StreamId(1), null))
    }

    private fun factory() = GrpcStreamInspectorFactory(maximumDeclaredMessageBytes = 1_024L)

    private fun request(
        contentType: String = "application/grpc",
        path: String = "/test.echo.EchoService/UnaryEcho",
    ) = RequestHead(
        method = HttpMethod.POST,
        target = RequestTarget.Absolute(
            scheme = HttpScheme.Standard(StandardHttpScheme.HTTPS),
            authority = com.devuloopers.knet.traffic.model.http.Authority("localhost", 8443),
            pathAndQuery = path,
        ),
        protocol = ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_2),
        headers = listOf(header("content-type", contentType)),
    )

    private fun header(name: String, value: String) = HeaderField(HeaderName(name), value)

    private fun frame(payload: ByteArray, compressionFlag: Int = 0): ByteArray = byteArrayOf(
        compressionFlag.toByte(),
        (payload.size ushr 24).toByte(),
        (payload.size ushr 16).toByte(),
        (payload.size ushr 8).toByte(),
        payload.size.toByte(),
    ) + payload
}

private class Slice(private val bytes: ByteArray) : ProxyPayloadSlice {
    override val size: Int = bytes.size

    override fun copyTo(
        destination: ByteArray,
        destinationOffset: Int,
        sourceOffset: Int,
        length: Int,
    ) {
        bytes.copyInto(destination, destinationOffset, sourceOffset, sourceOffset + length)
    }
}

private class RecordingExchangeCapture : ProxyExchangeCapture {
    override val exchangeId: ExchangeId = ExchangeId("exchange")
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
        errorCode: String,
    ) = Unit

    override fun observeResponse(response: ResponseHead, occurredAtEpochMillis: Long) = Unit

    override fun terminate(
        state: ExchangeState,
        timings: ExchangeTimings,
        occurredAtEpochMillis: Long,
        errorCode: String?,
    ) = Unit
}

private class RecordingMessageCapture(
    val metadata: ProxyMessageCaptureMetadata,
) : ProxyMessageCapture {
    override val messageId: ProtocolMessageId = metadata.messageId
    private val chunks = mutableListOf<ByteArray>()
    var state: ProtocolMessageState = ProtocolMessageState.IN_PROGRESS
    var errorCode: String? = null
    var observedBytes: Long = 0L

    override fun tryReservePayload(requestedBytes: Int): ProxyBodyReservation = object : ProxyBodyReservation {
        override val writableBytes: ByteArray = ByteArray(requestedBytes)

        override fun publish(occurredAtEpochMillis: Long): Boolean {
            chunks += writableBytes.copyOf()
            return true
        }

        override fun cancel() = Unit
    }

    override fun complete(observedBytes: Long, occurredAtEpochMillis: Long) {
        this.observedBytes = observedBytes
        state = ProtocolMessageState.COMPLETE
    }

    override fun terminate(
        observedBytes: Long,
        state: ProtocolMessageState,
        occurredAtEpochMillis: Long,
        errorCode: String?,
    ) {
        this.observedBytes = observedBytes
        this.state = state
        this.errorCode = errorCode
    }

    fun payload(): ByteArray = chunks.fold(ByteArray(0)) { accumulated, chunk -> accumulated + chunk }
}
