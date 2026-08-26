package com.devuloopers.knet.engine.websocket

import com.devuloopers.knet.application.contract.breakpoint.BreakpointBody
import com.devuloopers.knet.application.contract.breakpoint.ProtocolMessageBreakpointCandidate
import com.devuloopers.knet.application.contract.breakpoint.ProtocolMessageBreakpointDecision
import com.devuloopers.knet.application.contract.breakpoint.ProtocolMessageBreakpointGate
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.application.contract.breakpoint.ProtocolMessageInspectionInput
import com.devuloopers.knet.domain.rules.model.BreakpointProtocolId
import com.devuloopers.knet.engine.proxy.capture.ProxyBodyReservation
import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.engine.proxy.inspection.ProxyDuplexTransformResult
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.ProtocolMessageId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.message.ProtocolMessageKind
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebSocketBreakpointRuntimeTest {
    @Test
    fun `fragmented message with interleaved control frame retains exact wire order`() {
        val gate = RecordingWebSocketGate()
        val transformer = assertNotNull(factory(gate).create(request(), null, Capture()))
        transformer.onEstablished(switchingResponse(), 0L)
        val maskOne = byteArrayOf(1, 2, 3, 4)
        val maskTwo = byteArrayOf(5, 6, 7, 8)
        val first = WebSocketFrameDecoder.encode(
            WebSocketOpcode.TEXT,
            "hel".encodeToByteArray(),
            final = false,
            maskingKey = maskOne,
        )
        val ping = WebSocketFrameDecoder.encode(
            WebSocketOpcode.PING,
            byteArrayOf(9),
            maskingKey = byteArrayOf(9, 8, 7, 6),
        )
        val last = WebSocketFrameDecoder.encode(
            WebSocketOpcode.CONTINUATION,
            "lo".encodeToByteArray(),
            maskingKey = maskTwo,
        )

        assertContentEquals(ByteArray(0), transformer.transformClient(first, 1L))
        assertContentEquals(ByteArray(0), transformer.transformClient(ping, 2L))
        assertContentEquals(first + ping + last, transformer.transformClient(last, 3L))
        assertEquals(listOf("ping", "text"), gate.candidates.map { it.kind.value })
    }

    @Test
    fun `replacement preserves fragmentation controls and client masks`() {
        val gate = RecordingWebSocketGate(
            decisions = ArrayDeque(listOf(
                ProtocolMessageBreakpointDecision.ContinueUnchanged,
                ProtocolMessageBreakpointDecision.Replace(BreakpointBody("changed".encodeToByteArray())),
            )),
        )
        val transformer = assertNotNull(factory(gate).create(request(), null, Capture()))
        transformer.onEstablished(switchingResponse(), 0L)
        val first = WebSocketFrameDecoder.encode(
            WebSocketOpcode.TEXT,
            "one".encodeToByteArray(),
            final = false,
            maskingKey = byteArrayOf(1, 2, 3, 4),
        )
        val ping = WebSocketFrameDecoder.encode(
            WebSocketOpcode.PING,
            byteArrayOf(7),
            maskingKey = byteArrayOf(4, 3, 2, 1),
        )
        val last = WebSocketFrameDecoder.encode(
            WebSocketOpcode.CONTINUATION,
            "two".encodeToByteArray(),
            maskingKey = byteArrayOf(5, 6, 7, 8),
        )

        transformer.transformClient(first, 1L)
        transformer.transformClient(ping, 2L)
        val output = transformer.transformClient(last, 3L)
        val frames = assertIs<WebSocketDecodeResult.Frames>(
            WebSocketFrameDecoder(true, false).accept(output),
        ).values

        assertEquals(listOf(WebSocketOpcode.TEXT, WebSocketOpcode.PING, WebSocketOpcode.CONTINUATION), frames.map { it.opcode })
        assertContentEquals("changed".encodeToByteArray(), frames[0].payload + frames[2].payload)
        assertContentEquals(byteArrayOf(7), frames[1].payload)
        assertEquals(false, frames[0].final)
        assertEquals(true, frames[2].final)
    }

    @Test
    fun `compiled message criteria isolate direction kind subprotocol and sequence`() {
        val extension = WebSocketBreakpointExtension()
        val criteria = assertNotNull(extension.createCriteria(
            listOf(
                ProtocolCriteriaValue(WebSocketBreakpointProtocol.directionFieldId, "client"),
                ProtocolCriteriaValue(WebSocketBreakpointProtocol.kindFieldId, ProtocolMessageKind.TEXT.value),
                ProtocolCriteriaValue(WebSocketBreakpointProtocol.subprotocolFieldId, "chat"),
                ProtocolCriteriaValue(WebSocketBreakpointProtocol.sequenceFieldId, "2"),
            ),
        ))
        val compiled = assertNotNull(extension.compile(criteria))

        assertTrue(compiled.matches(extension.inspectMessage(messageInput(sequence = 2L))))
        assertFalse(compiled.matches(extension.inspectMessage(messageInput(sequence = 1L))))
        assertFalse(compiled.matches(extension.inspectMessage(
            messageInput(sequence = 2L, direction = TrafficDirection.SERVER_TO_CLIENT),
        )))
    }

    private fun factory(gate: ProtocolMessageBreakpointGate) = WebSocketBreakpointTransformerFactory(
        gate = gate,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        maximumEditableMessageBytes = 1_024,
    )

    private fun request() = HttpRequestSnapshot(
        RequestHead(
            method = HttpMethod.GET,
            target = RequestTarget.Absolute(
                scheme = HttpScheme.Standard(StandardHttpScheme.HTTP),
                authority = Authority("localhost", 8080),
                pathAndQuery = "/socket",
            ),
            protocol = ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_1_1),
            headers = listOf(
                HeaderField(HeaderName("connection"), "keep-alive, Upgrade"),
                HeaderField(HeaderName("upgrade"), "websocket"),
                HeaderField(HeaderName("sec-websocket-version"), "13"),
                HeaderField(HeaderName("sec-websocket-key"), "MDEyMzQ1Njc4OWFiY2RlZg=="),
                HeaderField(HeaderName("sec-websocket-protocol"), "chat, graphql-transport-ws"),
            ),
        ),
    )

    private fun messageInput(
        sequence: Long,
        direction: TrafficDirection = TrafficDirection.CLIENT_TO_SERVER,
    ) = ProtocolMessageInspectionInput(
        exchangeId = ExchangeId("websocket-inspection-exchange"),
        request = request(),
        messageId = ProtocolMessageId("message-$sequence-${direction.name}"),
        kind = ProtocolMessageKind.TEXT,
        direction = direction,
        sequence = sequence,
        declaredBytes = 5L,
        compressed = false,
        compressionEncoding = null,
        body = BreakpointBody("hello".encodeToByteArray()),
    )

    private fun switchingResponse() = ResponseHead(
        status = HttpStatus(101),
        protocol = ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_1_1),
        headers = listOf(
            HeaderField(HeaderName("connection"), "Upgrade"),
            HeaderField(HeaderName("upgrade"), "websocket"),
        ),
    )
}

private fun com.devuloopers.knet.engine.proxy.inspection.ProxyDuplexTransformer.transformClient(
    payload: ByteArray,
    occurredAtEpochMillis: Long,
): ByteArray = assertIs<ProxyDuplexTransformResult.Forward>(
    transform(TrafficDirection.CLIENT_TO_SERVER, payload, occurredAtEpochMillis)
        .toCompletableFuture().get(1, TimeUnit.SECONDS),
).copyPayload()

private class RecordingWebSocketGate(
    private val decisions: ArrayDeque<ProtocolMessageBreakpointDecision> = ArrayDeque(),
) : ProtocolMessageBreakpointGate {
    val candidates = mutableListOf<ProtocolMessageBreakpointCandidate>()

    override fun mayInterceptMessage(
        request: HttpRequestSnapshot,
        protocolRoute: List<BreakpointProtocolId>,
        direction: TrafficDirection,
    ): Boolean = direction == TrafficDirection.CLIENT_TO_SERVER

    override suspend fun interceptMessage(
        candidate: ProtocolMessageBreakpointCandidate,
    ): ProtocolMessageBreakpointDecision {
        candidates += candidate
        return decisions.removeFirstOrNull() ?: ProtocolMessageBreakpointDecision.ContinueUnchanged
    }

    override fun cancelProtocolMessages(exchangeId: ExchangeId) = Unit
}

private class Capture : ProxyExchangeCapture {
    override val exchangeId: ExchangeId = ExchangeId("websocket-breakpoint-exchange")

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
