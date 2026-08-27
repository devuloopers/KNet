package com.devuloopers.knet.engine.grpc

import com.devuloopers.knet.application.contract.breakpoint.BreakpointBody
import com.devuloopers.knet.application.contract.breakpoint.ProtocolMessageBreakpointCandidate
import com.devuloopers.knet.application.contract.breakpoint.ProtocolMessageBreakpointDecision
import com.devuloopers.knet.application.contract.breakpoint.ProtocolMessageBreakpointGate
import com.devuloopers.knet.domain.rules.model.BreakpointProtocolId
import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.engine.proxy.inspection.ProxyStreamTransformResult
import com.devuloopers.knet.engine.proxy.inspection.ProxyStreamTransformer
import com.devuloopers.knet.engine.proxy.inspection.ProxyStreamTransformerFactory
import com.devuloopers.knet.traffic.id.ProtocolMessageId
import com.devuloopers.knet.traffic.id.StreamId
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficTerminationCode
import com.devuloopers.knet.traffic.model.TrafficTerminationReason
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.ResponseHead
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.StandardApplicationProtocol
import com.devuloopers.knet.traffic.model.message.ProtocolMessageKind
import com.devuloopers.knet.traffic.model.message.MessageProtocolId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Creates a bounded native-gRPC message gate only when a compiled message rule can match.
 * Ordinary gRPC traffic therefore stays on the proxy's observation-only streaming path.
 */
class GrpcMessageBreakpointTransformerFactory(
    private val gate: ProtocolMessageBreakpointGate,
    private val scope: CoroutineScope,
    private val maximumEditableMessageBytes: Int = DEFAULT_MAXIMUM_EDITABLE_MESSAGE_BYTES,
) : ProxyStreamTransformerFactory {
    init {
        require(maximumEditableMessageBytes > 0) { "Maximum editable gRPC message bytes must be positive." }
    }

    override fun create(
        request: HttpRequestSnapshot,
        streamId: StreamId?,
        capture: ProxyExchangeCapture?,
    ): ProxyStreamTransformer? {
        if (capture == null || request.head.method != HttpMethod.POST) return null
        val isHttpTwo = (request.head.protocol as? ApplicationProtocol.Standard)?.value ==
            StandardApplicationProtocol.HTTP_2
        if (!isHttpTwo) return null
        if (!GrpcProtocol.isNativeContentType(GrpcProtocol.header(request.head.headers, CONTENT_TYPE))) return null
        if (GrpcMethodIdentity.fromTarget(request.head.target) == null) return null
        val interceptClient = gate.mayInterceptMessage(
            request,
            listOf(GrpcBreakpointProtocol.id),
            TrafficDirection.CLIENT_TO_SERVER,
        )
        val interceptServer = gate.mayInterceptMessage(
            request,
            listOf(GrpcBreakpointProtocol.id),
            TrafficDirection.SERVER_TO_CLIENT,
        )
        if (!interceptClient && !interceptServer) return null
        return GrpcMessageBreakpointTransformer(
            request = request,
            exchangeCapture = capture,
            streamId = streamId,
            gate = gate,
            scope = scope,
            requestEncoding = GrpcProtocol.header(request.head.headers, GRPC_ENCODING),
            interceptClient = interceptClient,
            interceptServer = interceptServer,
            maximumEditableMessageBytes = maximumEditableMessageBytes,
        )
    }

    private companion object {
        const val CONTENT_TYPE: String = "content-type"
        const val GRPC_ENCODING: String = "grpc-encoding"
        const val DEFAULT_MAXIMUM_EDITABLE_MESSAGE_BYTES: Int = 10 * 1_024 * 1_024
    }
}

private class GrpcMessageBreakpointTransformer(
    private val request: HttpRequestSnapshot,
    private val exchangeCapture: ProxyExchangeCapture,
    private val streamId: StreamId?,
    private val gate: ProtocolMessageBreakpointGate,
    private val scope: CoroutineScope,
    requestEncoding: String?,
    interceptClient: Boolean,
    interceptServer: Boolean,
    maximumEditableMessageBytes: Int,
) : ProxyStreamTransformer {
    private val cancelled = AtomicBoolean(false)
    private val clientGate = DirectionMessageGate(
        request = request,
        exchangeCapture = exchangeCapture,
        streamId = streamId,
        direction = TrafficDirection.CLIENT_TO_SERVER,
        gate = gate,
        enabled = interceptClient,
        compressionEncoding = requestEncoding,
        maximumEditableMessageBytes = maximumEditableMessageBytes,
    )
    private val serverGate = DirectionMessageGate(
        request = request,
        exchangeCapture = exchangeCapture,
        streamId = streamId,
        direction = TrafficDirection.SERVER_TO_CLIENT,
        gate = gate,
        enabled = interceptServer,
        compressionEncoding = null,
        maximumEditableMessageBytes = maximumEditableMessageBytes,
    )

    override fun onResponse(response: ResponseHead, occurredAtEpochMillis: Long) {
        serverGate.transportRecognized = GrpcProtocol.isNativeContentType(
            GrpcProtocol.header(response.headers, CONTENT_TYPE),
        )
        serverGate.compressionEncoding = GrpcProtocol.header(response.headers, GRPC_ENCODING)
    }

    override fun onTrailers(
        direction: TrafficDirection,
        trailers: List<HeaderField>,
        occurredAtEpochMillis: Long,
    ) {
        if (direction == TrafficDirection.SERVER_TO_CLIENT) {
            serverGate.compressionEncoding =
                GrpcProtocol.header(trailers, GRPC_ENCODING) ?: serverGate.compressionEncoding
        }
    }

    override fun transform(
        direction: TrafficDirection,
        payload: ByteArray,
        endOfDirection: Boolean,
        occurredAtEpochMillis: Long,
    ): CompletionStage<ProxyStreamTransformResult> {
        if (cancelled.get()) {
            return CompletableFuture.completedFuture(
                ProxyStreamTransformResult.DropStream(grpcBreakpointTermination(STREAM_CANCELLED)),
            )
        }
        val future = CompletableFuture<ProxyStreamTransformResult>()
        scope.launch {
            val result = runCatching {
                directionGate(direction).transform(payload, endOfDirection, occurredAtEpochMillis)
            }.getOrElse {
                ProxyStreamTransformResult.DropStream(grpcBreakpointTermination(TRANSFORM_FAILED))
            }
            future.complete(result)
        }
        return future
    }

    override fun cancel(reason: TrafficTerminationReason?) {
        if (!cancelled.compareAndSet(false, true)) return
        clientGate.cancel()
        serverGate.cancel()
        gate.cancelProtocolMessages(exchangeCapture.exchangeId)
    }

    private fun directionGate(direction: TrafficDirection): DirectionMessageGate = when (direction) {
        TrafficDirection.CLIENT_TO_SERVER -> clientGate
        TrafficDirection.SERVER_TO_CLIENT -> serverGate
    }

    private companion object {
        const val CONTENT_TYPE: String = "content-type"
        const val GRPC_ENCODING: String = "grpc-encoding"
        const val STREAM_CANCELLED: String = "grpc_breakpoint_stream_cancelled"
        const val TRANSFORM_FAILED: String = "grpc_breakpoint_transform_failed"
    }
}

/** Direction-confined incremental deframer/reframer used only while a message rule is active. */
@OptIn(ExperimentalUuidApi::class)
private class DirectionMessageGate(
    private val request: HttpRequestSnapshot,
    private val exchangeCapture: ProxyExchangeCapture,
    private val streamId: StreamId?,
    private val direction: TrafficDirection,
    private val gate: ProtocolMessageBreakpointGate,
    private val enabled: Boolean,
    compressionEncoding: String?,
    private val maximumEditableMessageBytes: Int,
) {
    var transportRecognized: Boolean = direction == TrafficDirection.CLIENT_TO_SERVER
    var compressionEncoding: String? = compressionEncoding
    private val prefix = ByteArray(PREFIX_BYTES)
    private var prefixCount = 0
    private var payload: ByteArray? = null
    private var payloadCount = 0
    private var compressed = false
    private var sequence = 0L
    private var bypass = !enabled
    private var cancelled = false

    suspend fun transform(
        input: ByteArray,
        endOfDirection: Boolean,
        occurredAtEpochMillis: Long,
    ): ProxyStreamTransformResult {
        if (cancelled) {
            return ProxyStreamTransformResult.DropStream(grpcBreakpointTermination(STREAM_CANCELLED))
        }
        if (bypass || !transportRecognized) return ProxyStreamTransformResult.Forward(input)
        val output = ByteArrayOutputStream(input.size)
        var inputOffset = 0
        while (inputOffset < input.size) {
            if (prefixCount < PREFIX_BYTES) {
                val count = minOf(PREFIX_BYTES - prefixCount, input.size - inputOffset)
                input.copyInto(prefix, prefixCount, inputOffset, inputOffset + count)
                prefixCount += count
                inputOffset += count
                if (prefixCount < PREFIX_BYTES) continue

                val flag = prefix[0].toInt() and 0xff
                val declared = declaredLength(prefix)
                if ((flag != 0 && flag != 1) || declared > maximumEditableMessageBytes.toLong()) {
                    output.write(prefix)
                    output.write(input, inputOffset, input.size - inputOffset)
                    resetFrame()
                    bypass = true
                    inputOffset = input.size
                    continue
                }
                compressed = flag == 1
                payload = ByteArray(declared.toInt())
                payloadCount = 0
                if (declared == 0L) {
                    when (val result = resolveMessage(ByteArray(0), occurredAtEpochMillis)) {
                        is ProxyStreamTransformResult.Forward -> output.write(result.payload)
                        is ProxyStreamTransformResult.DropStream -> return result
                    }
                }
                continue
            }

            val current = checkNotNull(payload)
            val count = minOf(current.size - payloadCount, input.size - inputOffset)
            input.copyInto(current, payloadCount, inputOffset, inputOffset + count)
            payloadCount += count
            inputOffset += count
            if (payloadCount == current.size) {
                when (val result = resolveMessage(current, occurredAtEpochMillis)) {
                    is ProxyStreamTransformResult.Forward -> output.write(result.payload)
                    is ProxyStreamTransformResult.DropStream -> return result
                }
            }
        }

        if (endOfDirection && (prefixCount > 0 || payload != null)) {
            // Malformed/early-EOF data remains byte-for-byte transparent; breakpoints never repair wire errors.
            output.write(prefix, 0, prefixCount)
            payload?.let { held -> output.write(held, 0, payloadCount) }
            resetFrame()
        }
        return ProxyStreamTransformResult.Forward(output.toByteArray())
    }

    fun cancel() {
        cancelled = true
        resetFrame()
    }

    private suspend fun resolveMessage(
        originalBody: ByteArray,
        occurredAtEpochMillis: Long,
    ): ProxyStreamTransformResult {
        val messageId = ProtocolMessageId(Uuid.random().toString())
        val decision = gate.interceptMessage(
            ProtocolMessageBreakpointCandidate(
                exchangeId = exchangeCapture.exchangeId,
                messageId = messageId,
                protocolRoute = listOf(BreakpointProtocolId("grpc")),
                kind = ProtocolMessageKind.DATA,
                request = request,
                direction = direction,
                sequence = ++sequence,
                declaredBytes = originalBody.size.toLong(),
                compressed = compressed,
                compressionEncoding = compressionEncoding,
                body = BreakpointBody(originalBody),
                retainedTransportBytes = PREFIX_BYTES.toLong(),
                startedAtEpochMillis = occurredAtEpochMillis,
            ),
        )
        val result = when (decision) {
            ProtocolMessageBreakpointDecision.ContinueUnchanged ->
                ProxyStreamTransformResult.Forward(frame(compressed, originalBody))
            is ProtocolMessageBreakpointDecision.Replace ->
                ProxyStreamTransformResult.Forward(frame(compressed, decision.body.copyBytes()))
            ProtocolMessageBreakpointDecision.DropStream ->
                ProxyStreamTransformResult.DropStream(grpcBreakpointTermination(MESSAGE_DROPPED))
        }
        resetFrame()
        return result
    }

    private fun resetFrame() {
        prefixCount = 0
        payload = null
        payloadCount = 0
        compressed = false
    }

    private companion object {
        const val PREFIX_BYTES: Int = 5
        const val MESSAGE_DROPPED: String = "grpc_breakpoint_message_dropped"
        const val STREAM_CANCELLED: String = "grpc_breakpoint_stream_cancelled"

        fun declaredLength(prefix: ByteArray): Long =
            ((prefix[1].toLong() and 0xffL) shl 24) or
                ((prefix[2].toLong() and 0xffL) shl 16) or
                ((prefix[3].toLong() and 0xffL) shl 8) or
                (prefix[4].toLong() and 0xffL)

        fun frame(compressed: Boolean, body: ByteArray): ByteArray = ByteArray(PREFIX_BYTES + body.size).also { frame ->
            frame[0] = if (compressed) 1 else 0
            frame[1] = (body.size ushr 24).toByte()
            frame[2] = (body.size ushr 16).toByte()
            frame[3] = (body.size ushr 8).toByte()
            frame[4] = body.size.toByte()
            body.copyInto(frame, PREFIX_BYTES)
        }
    }
}

private fun grpcBreakpointTermination(code: String): TrafficTerminationReason =
    TrafficTerminationReason.Protocol(
        protocol = MessageProtocolId.GRPC,
        code = TrafficTerminationCode(code),
    )
