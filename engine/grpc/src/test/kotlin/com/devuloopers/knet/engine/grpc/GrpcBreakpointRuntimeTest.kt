package com.devuloopers.knet.engine.grpc

import com.devuloopers.knet.application.contract.breakpoint.BreakpointBody
import com.devuloopers.knet.application.contract.breakpoint.BreakpointProtocolRegistry
import com.devuloopers.knet.application.contract.breakpoint.BreakpointRuleSuggestionInput
import com.devuloopers.knet.application.contract.breakpoint.ProtocolMessageBreakpointCandidate
import com.devuloopers.knet.application.contract.breakpoint.ProtocolMessageBreakpointDecision
import com.devuloopers.knet.application.contract.breakpoint.ProtocolMessageBreakpointGate
import com.devuloopers.knet.application.contract.breakpoint.ProtocolMessageInspectionInput
import com.devuloopers.knet.domain.rules.model.BreakpointProtocolId
import com.devuloopers.knet.engine.proxy.capture.ProxyBodyReservation
import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.engine.proxy.inspection.ProxyStreamTransformResult
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.ProtocolMessageId
import com.devuloopers.knet.traffic.id.StreamId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.ExchangeTerminalOutcome
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficTerminationReason
import com.devuloopers.knet.traffic.model.body.ContentEncoding
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.Authority
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.HeaderName
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.HttpScheme
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GrpcBreakpointRuntimeTest {
    @Test
    fun `extension distinguishes operation direction and message sequence on one endpoint`() {
        val extension = GrpcBreakpointExtension()
        val registry = BreakpointProtocolRegistry(listOf(extension))
        val suggested = assertNotNull(
            extension.suggestCriteria(
                BreakpointRuleSuggestionInput(request(), requestBody = null, requestBodyComplete = false),
            ),
        )
        val compiled = assertNotNull(registry.compile(suggested))

        val matching = extension.inspectMessage(messageInput(TrafficDirection.CLIENT_TO_SERVER, sequence = 1L))
        val wrongMethod = extension.inspectMessage(
            messageInput(
                TrafficDirection.CLIENT_TO_SERVER,
                sequence = 1L,
                path = "/test.echo.EchoService/OtherMethod",
            ),
        )
        val wrongDirection = extension.inspectMessage(messageInput(TrafficDirection.SERVER_TO_CLIENT, sequence = 1L))

        assertTrue(registry.matches(compiled, matching))
        assertTrue(!registry.matches(compiled, wrongMethod))
        // Smart suggestions intentionally match both directions unless the user narrows the rule.
        assertTrue(registry.matches(compiled, wrongDirection))
    }

    @Test
    fun `factory stays off the body path when no grpc message rule can match`() {
        val gate = RecordingGate(enabled = false)
        val factory = factory(gate)

        assertNull(factory.create(request(), StreamId(3), Capture()))
        assertNull(factory.create(request(protocol = StandardApplicationProtocol.HTTP_1_1), null, Capture()))
    }

    @Test
    fun `split grpc message pauses once and forwards its original frame`() {
        val gate = RecordingGate(decisions = ArrayDeque(listOf(ProtocolMessageBreakpointDecision.ContinueUnchanged)))
        val transformer = assertNotNull(factory(gate).create(request(), StreamId(3), Capture()))
        val wire = frame("hello".encodeToByteArray())

        val prefix = assertIs<ProxyStreamTransformResult.Forward>(
            transformer.transform(TrafficDirection.CLIENT_TO_SERVER, wire.copyOfRange(0, 2), false, 1L)
                .toCompletableFuture().get(1, TimeUnit.SECONDS),
        )
        val remainder = assertIs<ProxyStreamTransformResult.Forward>(
            transformer.transform(TrafficDirection.CLIENT_TO_SERVER, wire.copyOfRange(2, wire.size), true, 2L)
                .toCompletableFuture().get(1, TimeUnit.SECONDS),
        )

        assertContentEquals(ByteArray(0), prefix.payload)
        assertContentEquals(wire, remainder.payload)
        assertEquals(1, gate.candidates.size)
        assertContentEquals("hello".encodeToByteArray(), gate.candidates.single().body.copyBytes())
    }

    @Test
    fun `replacement recalculates envelope and drop terminates only the stream`() {
        val replacement = "changed".encodeToByteArray()
        val gate = RecordingGate(
            decisions = ArrayDeque(
                listOf(
                    ProtocolMessageBreakpointDecision.Replace(BreakpointBody(replacement)),
                    ProtocolMessageBreakpointDecision.DropStream,
                ),
            ),
        )
        val transformer = assertNotNull(factory(gate).create(request(), StreamId(5), Capture()))

        val replaced = assertIs<ProxyStreamTransformResult.Forward>(
            transformer.transform(TrafficDirection.CLIENT_TO_SERVER, frame(byteArrayOf(1)), false, 1L)
                .toCompletableFuture().get(1, TimeUnit.SECONDS),
        )
        val dropped = assertIs<ProxyStreamTransformResult.DropStream>(
            transformer.transform(TrafficDirection.CLIENT_TO_SERVER, frame(byteArrayOf(2)), true, 2L)
                .toCompletableFuture().get(1, TimeUnit.SECONDS),
        )

        assertContentEquals(frame(replacement), replaced.payload)
        assertEquals("grpc_breakpoint_message_dropped", dropped.reason.code.value)
    }

    private fun factory(gate: ProtocolMessageBreakpointGate) = GrpcMessageBreakpointTransformerFactory(
        gate = gate,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        maximumEditableMessageBytes = 1_024,
    )

    private fun messageInput(
        direction: TrafficDirection,
        sequence: Long,
        path: String = "/test.echo.EchoService/UnaryEcho",
    ) = ProtocolMessageInspectionInput(
        exchangeId = ExchangeId("grpc-inspection-exchange"),
        request = request(path = path),
        messageId = ProtocolMessageId("message-$sequence-${direction.name}"),
        kind = com.devuloopers.knet.traffic.model.message.ProtocolMessageKind.DATA,
        direction = direction,
        sequence = sequence,
        declaredBytes = 1L,
        compressed = false,
        compressionEncoding = null,
        body = BreakpointBody(byteArrayOf(1)),
    )

    private fun request(
        path: String = "/test.echo.EchoService/UnaryEcho",
        protocol: StandardApplicationProtocol = StandardApplicationProtocol.HTTP_2,
    ) = HttpRequestSnapshot(
        RequestHead(
            method = HttpMethod.POST,
            target = RequestTarget.Absolute(
                scheme = HttpScheme.Standard(StandardHttpScheme.HTTPS),
                authority = Authority("localhost", 8443),
                pathAndQuery = path,
            ),
            protocol = ApplicationProtocol.Standard(protocol),
            headers = listOf(HeaderField(HeaderName("content-type"), "application/grpc")),
        ),
    )

    private fun frame(body: ByteArray): ByteArray = byteArrayOf(
        0,
        (body.size ushr 24).toByte(),
        (body.size ushr 16).toByte(),
        (body.size ushr 8).toByte(),
        body.size.toByte(),
    ) + body
}

private class RecordingGate(
    private val enabled: Boolean = true,
    private val decisions: ArrayDeque<ProtocolMessageBreakpointDecision> = ArrayDeque(),
) : ProtocolMessageBreakpointGate {
    val candidates = mutableListOf<ProtocolMessageBreakpointCandidate>()

    override fun mayInterceptMessage(
        request: HttpRequestSnapshot,
        protocolRoute: List<BreakpointProtocolId>,
        direction: TrafficDirection,
    ): Boolean = enabled && direction == TrafficDirection.CLIENT_TO_SERVER

    override suspend fun interceptMessage(
        candidate: ProtocolMessageBreakpointCandidate,
    ): ProtocolMessageBreakpointDecision {
        candidates += candidate
        return decisions.removeFirstOrNull() ?: ProtocolMessageBreakpointDecision.ContinueUnchanged
    }

    override fun cancelProtocolMessages(exchangeId: ExchangeId) = Unit
}

private class Capture : ProxyExchangeCapture {
    override val exchangeId: ExchangeId = ExchangeId("breakpoint-exchange")
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
