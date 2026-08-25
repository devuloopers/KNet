package com.devuloopers.knet.engine.websocket

import com.devuloopers.knet.engine.proxy.capture.ProxyBodyReservation
import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyMessageCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyMessageCaptureMetadata
import com.devuloopers.knet.engine.proxy.inspection.ProxyPayloadSlice
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.ProtocolMessageId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.TrafficDirection
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
import com.devuloopers.knet.traffic.model.http.StandardApplicationProtocol
import com.devuloopers.knet.traffic.model.http.StandardHttpScheme
import com.devuloopers.knet.traffic.model.message.ProtocolMessageKind
import com.devuloopers.knet.traffic.model.message.ProtocolMessageState
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WebSocketDuplexInspectorTest {
    @Test
    fun `fragmented data and interleaved control frames become bounded logical messages`() {
        val capture = WebSocketRecordingExchangeCapture()
        val inspector = assertNotNull(WebSocketDuplexInspectorFactory(1_024).create(request(), null, capture))
        inspector.onEstablished(switchingResponse(), 1L)
        val first = WebSocketFrameDecoder.encode(
            opcode = WebSocketOpcode.TEXT,
            payload = "hel".encodeToByteArray(),
            final = false,
            maskingKey = byteArrayOf(1, 2, 3, 4),
        )
        val ping = WebSocketFrameDecoder.encode(
            opcode = WebSocketOpcode.PING,
            payload = byteArrayOf(7),
            maskingKey = byteArrayOf(4, 3, 2, 1),
        )
        val last = WebSocketFrameDecoder.encode(
            opcode = WebSocketOpcode.CONTINUATION,
            payload = "lo".encodeToByteArray(),
            maskingKey = byteArrayOf(5, 6, 7, 8),
        )

        inspector.onPayload(TrafficDirection.CLIENT_TO_SERVER, WebSocketSlice(first.copyOfRange(0, 3)), 2L)
        inspector.onPayload(TrafficDirection.CLIENT_TO_SERVER, WebSocketSlice(first.copyOfRange(3, first.size) + ping), 3L)
        inspector.onPayload(TrafficDirection.CLIENT_TO_SERVER, WebSocketSlice(last), 4L)

        assertEquals(listOf(ProtocolMessageKind.TEXT, ProtocolMessageKind.PING), capture.messages.map { it.metadata.kind })
        assertEquals(listOf(1L, 2L), capture.messages.map { it.metadata.messageSequence })
        assertEquals(listOf(ProtocolMessageState.COMPLETE, ProtocolMessageState.COMPLETE), capture.messages.map { it.state })
        assertContentEquals("hello".encodeToByteArray(), capture.messages[0].payload())
        assertContentEquals(byteArrayOf(7), capture.messages[1].payload())
    }

    @Test
    fun `both directions own independent masking rules and sequences`() {
        val capture = WebSocketRecordingExchangeCapture()
        val inspector = assertNotNull(WebSocketDuplexInspectorFactory(1_024).create(request(), null, capture))
        inspector.onEstablished(switchingResponse(), 1L)
        val outbound = WebSocketFrameDecoder.encode(
            WebSocketOpcode.BINARY,
            byteArrayOf(1, 2),
            maskingKey = byteArrayOf(1, 1, 1, 1),
        )
        val inbound = WebSocketFrameDecoder.encode(WebSocketOpcode.TEXT, "reply".encodeToByteArray())

        inspector.onPayload(TrafficDirection.CLIENT_TO_SERVER, WebSocketSlice(outbound), 2L)
        inspector.onPayload(TrafficDirection.SERVER_TO_CLIENT, WebSocketSlice(inbound), 3L)

        assertEquals(listOf(1L, 1L), capture.messages.map { it.metadata.messageSequence })
        assertEquals(
            listOf(TrafficDirection.CLIENT_TO_SERVER, TrafficDirection.SERVER_TO_CLIENT),
            capture.messages.map { it.metadata.direction },
        )
        assertContentEquals(byteArrayOf(1, 2), capture.messages[0].payload())
        assertContentEquals("reply".encodeToByteArray(), capture.messages[1].payload())
    }

    @Test
    fun `ordinary http request does not create a websocket inspector`() {
        val ordinary = HttpRequestSnapshot(
            request().head.copy(headers = emptyList()),
        )

        assertNull(WebSocketDuplexInspectorFactory().create(ordinary, null, WebSocketRecordingExchangeCapture()))
    }

    @Test
    fun `malformed websocket key does not claim a duplex upgrade`() {
        val malformed = HttpRequestSnapshot(
            request().head.copy(
                headers = request().head.headers.map { header ->
                    if (header.name.value == "sec-websocket-key") header("sec-websocket-key", "too-short") else header
                },
            ),
        )

        assertNull(WebSocketDuplexInspectorFactory().create(malformed, null, WebSocketRecordingExchangeCapture()))
    }

    private fun request() = HttpRequestSnapshot(
        RequestHead(
            method = HttpMethod.GET,
            target = RequestTarget.Absolute(
                scheme = HttpScheme.Standard(StandardHttpScheme.HTTP),
                authority = Authority("localhost", 8080),
                pathAndQuery = "/lab/v1/websocket/echo",
            ),
            protocol = ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_1_1),
            headers = listOf(
                header("connection", "keep-alive, Upgrade"),
                header("upgrade", "websocket"),
                header("sec-websocket-version", "13"),
                header("sec-websocket-key", "MDEyMzQ1Njc4OWFiY2RlZg=="),
            ),
        ),
    )

    private fun switchingResponse() = ResponseHead(
        status = HttpStatus(101),
        protocol = ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_1_1),
        headers = listOf(header("connection", "Upgrade"), header("upgrade", "websocket")),
    )

    private fun header(name: String, value: String) = HeaderField(HeaderName(name), value)
}

private class WebSocketSlice(private val bytes: ByteArray) : ProxyPayloadSlice {
    override val size: Int = bytes.size

    override fun indexOf(value: Byte, startIndex: Int): Int =
        (startIndex until bytes.size).firstOrNull { bytes[it] == value } ?: -1

    override fun copyTo(
        destination: ByteArray,
        destinationOffset: Int,
        sourceOffset: Int,
        length: Int,
    ) {
        bytes.copyInto(destination, destinationOffset, sourceOffset, sourceOffset + length)
    }
}

private class WebSocketRecordingExchangeCapture : ProxyExchangeCapture {
    override val exchangeId: ExchangeId = ExchangeId("websocket-exchange")
    val messages = mutableListOf<WebSocketRecordingMessageCapture>()

    override fun startMessage(metadata: ProxyMessageCaptureMetadata): ProxyMessageCapture =
        WebSocketRecordingMessageCapture(metadata).also(messages::add)

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

private class WebSocketRecordingMessageCapture(
    val metadata: ProxyMessageCaptureMetadata,
) : ProxyMessageCapture {
    override val messageId: ProtocolMessageId = metadata.messageId
    private val chunks = mutableListOf<ByteArray>()
    var state: ProtocolMessageState = ProtocolMessageState.IN_PROGRESS

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
        errorCode: String?,
    ) {
        this.state = state
    }

    fun payload(): ByteArray = chunks.fold(ByteArray(0), ByteArray::plus)
}
