package com.devuloopers.knet.engine.sse.breakpoint

import com.devuloopers.knet.application.port.breakpoint.BreakpointBody
import com.devuloopers.knet.application.port.breakpoint.BreakpointProtocolRegistry
import com.devuloopers.knet.application.port.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.application.port.breakpoint.ProtocolMessageBreakpointCandidate
import com.devuloopers.knet.application.port.breakpoint.ProtocolMessageBreakpointDecision
import com.devuloopers.knet.application.port.breakpoint.ProtocolMessageBreakpointGate
import com.devuloopers.knet.application.port.breakpoint.ProtocolMessageInspectionInput
import com.devuloopers.knet.domain.rules.model.BreakpointProtocolId
import com.devuloopers.knet.engine.proxy.capture.ProxyBodyReservation
import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.engine.proxy.inspection.ProxyStreamTransformResult
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
import com.devuloopers.knet.traffic.model.message.ProtocolMessageKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SseBreakpointRuntimeTest {
    @Test
    fun `compiled rule distinguishes event type id and data on one endpoint`() {
        val extension = SseBreakpointExtension()
        val registry = BreakpointProtocolRegistry(listOf(extension))
        val criteria = assertNotNull(
            extension.createCriteria(
                listOf(
                    ProtocolCriteriaValue(SseBreakpointProtocol.eventTypeFieldId, "price-*"),
                    ProtocolCriteriaValue(SseBreakpointProtocol.eventIdFieldId, "order-?"),
                    ProtocolCriteriaValue(SseBreakpointProtocol.dataFieldId, "accepted"),
                ),
            ),
        )
        val compiled = assertNotNull(registry.compile(criteria))

        val matching = extension.inspectMessage(messageInput("id: order-7\nevent: price-update\ndata: accepted\n\n"))
        val wrongType = extension.inspectMessage(messageInput("id: order-7\nevent: inventory\ndata: accepted\n\n", 2L))

        assertTrue(registry.matches(compiled, matching))
        assertFalse(registry.matches(compiled, wrongType))
    }

    @Test
    fun `split records continue replace and drop through one bounded transformer`() {
        val replacement = "event: changed\ndata: replacement\n\n".encodeToByteArray()
        val gate = RecordingSseGate(
            ArrayDeque(
                listOf(
                    ProtocolMessageBreakpointDecision.ContinueUnchanged,
                    ProtocolMessageBreakpointDecision.Replace(BreakpointBody(replacement)),
                    ProtocolMessageBreakpointDecision.DropStream,
                ),
            ),
        )
        val transformer = assertNotNull(factory(gate).create(request(), null, Capture()))
        transformer.onResponse(response(), 0L)

        val firstPrefix = transform(transformer, "event: first\n".encodeToByteArray(), false, 1L)
        val firstEnd = transform(transformer, "data: one\n\n".encodeToByteArray(), false, 2L)
        val second = transform(transformer, "event: second\ndata: two\n\n".encodeToByteArray(), false, 3L)
        val third = transformer.transform(
            TrafficDirection.SERVER_TO_CLIENT,
            "event: third\ndata: three\n\n".encodeToByteArray(),
            true,
            4L,
        ).toCompletableFuture().get(1, TimeUnit.SECONDS)

        assertContentEquals(ByteArray(0), firstPrefix)
        assertContentEquals("event: first\ndata: one\n\n".encodeToByteArray(), firstEnd)
        assertContentEquals(replacement, second)
        assertIs<ProxyStreamTransformResult.DropStream>(third)
        assertEquals(
            listOf("first", "second", "third").map { "event: $it" },
            gate.candidates.map { it.body.copyBytes().decodeToString().lineSequence().first() },
        )
    }

    @Test
    fun `replacement validator accepts exactly one terminated record`() {
        val extension = SseBreakpointExtension()
        val input = messageInput("data: original\n\n")

        assertTrue(extension.validateMessageReplacement(input, BreakpointBody("data: changed\n\n".encodeToByteArray())))
        assertFalse(extension.validateMessageReplacement(input, BreakpointBody("data: incomplete".encodeToByteArray())))
        assertFalse(extension.validateMessageReplacement(input, BreakpointBody("data: one\n\ndata: two\n\n".encodeToByteArray())))
    }

    private fun factory(gate: ProtocolMessageBreakpointGate) = SseBreakpointTransformerFactory(
        gate = gate,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    )

    private fun transform(
        transformer: com.devuloopers.knet.engine.proxy.inspection.ProxyStreamTransformer,
        bytes: ByteArray,
        end: Boolean,
        time: Long,
    ): ByteArray = assertIs<ProxyStreamTransformResult.Forward>(
        transformer.transform(TrafficDirection.SERVER_TO_CLIENT, bytes, end, time)
            .toCompletableFuture().get(1, TimeUnit.SECONDS),
    ).payload

    private fun messageInput(body: String, sequence: Long = 1L) = ProtocolMessageInspectionInput(
        exchangeId = ExchangeId("sse-inspection"),
        request = request(),
        messageId = ProtocolMessageId("sse-message-$sequence"),
        kind = ProtocolMessageKind.RECORD,
        direction = TrafficDirection.SERVER_TO_CLIENT,
        sequence = sequence,
        declaredBytes = body.encodeToByteArray().size.toLong(),
        compressed = false,
        compressionEncoding = null,
        body = BreakpointBody(body.encodeToByteArray()),
    )

    private fun request() = HttpRequestSnapshot(
        RequestHead(
            method = HttpMethod.GET,
            target = RequestTarget.Absolute(HttpScheme.fromToken("https"), Authority("events.test"), "/events"),
            protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
            headers = listOf(HeaderField(HeaderName("accept"), "text/event-stream")),
        ),
    )

    private fun response() = ResponseHead(
        protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
        status = HttpStatus(200),
        reasonPhrase = "OK",
        headers = listOf(HeaderField(HeaderName("content-type"), "text/event-stream")),
    )
}

private class RecordingSseGate(
    private val decisions: ArrayDeque<ProtocolMessageBreakpointDecision>,
) : ProtocolMessageBreakpointGate {
    val candidates = mutableListOf<ProtocolMessageBreakpointCandidate>()

    override fun mayInterceptMessage(
        request: HttpRequestSnapshot,
        protocolRoute: List<BreakpointProtocolId>,
        direction: TrafficDirection,
    ): Boolean = direction == TrafficDirection.SERVER_TO_CLIENT && SseBreakpointProtocol.id in protocolRoute

    override suspend fun interceptMessage(
        candidate: ProtocolMessageBreakpointCandidate,
    ): ProtocolMessageBreakpointDecision {
        candidates += candidate
        return decisions.removeFirst()
    }

    override fun cancelProtocolMessages(exchangeId: ExchangeId) = Unit
}

private class Capture : ProxyExchangeCapture {
    override val exchangeId: ExchangeId = ExchangeId("sse-breakpoint-exchange")

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
