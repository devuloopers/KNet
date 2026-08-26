package com.devuloopers.knet.engine.sse.breakpoint

import com.devuloopers.knet.application.contract.breakpoint.BreakpointBody
import com.devuloopers.knet.application.contract.breakpoint.ProtocolMessageBreakpointCandidate
import com.devuloopers.knet.application.contract.breakpoint.ProtocolMessageBreakpointDecision
import com.devuloopers.knet.application.contract.breakpoint.ProtocolMessageBreakpointGate
import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.engine.proxy.inspection.ProxyStreamTransformResult
import com.devuloopers.knet.engine.proxy.inspection.ProxyStreamTransformer
import com.devuloopers.knet.engine.proxy.inspection.ProxyStreamTransformerFactory
import com.devuloopers.knet.engine.sse.encoding.SseContentCodecPlanResult
import com.devuloopers.knet.engine.sse.encoding.SseContentCodecRegistry
import com.devuloopers.knet.engine.sse.encoding.SseContentCodecResult
import com.devuloopers.knet.engine.sse.encoding.SseContentDecoder
import com.devuloopers.knet.engine.sse.encoding.SseContentEncoder
import com.devuloopers.knet.engine.sse.protocol.SseIncrementalParser
import com.devuloopers.knet.engine.sse.protocol.SseLimits
import com.devuloopers.knet.engine.sse.protocol.SseParseResult
import com.devuloopers.knet.engine.sse.protocol.SseProtocol
import com.devuloopers.knet.traffic.id.ProtocolMessageId
import com.devuloopers.knet.traffic.id.StreamId
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.http.ResponseHead
import com.devuloopers.knet.traffic.model.message.ProtocolMessageKind
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Creates a bounded SSE response-record gate only when an enabled SSE rule may match the request. */
class SseBreakpointTransformerFactory(
    private val gate: ProtocolMessageBreakpointGate,
    private val scope: CoroutineScope,
    private val limits: SseLimits = SseLimits(),
) : ProxyStreamTransformerFactory {
    override fun create(
        request: HttpRequestSnapshot,
        streamId: StreamId?,
        capture: ProxyExchangeCapture?,
    ): ProxyStreamTransformer? {
        val admittedCapture = capture ?: return null
        if (!gate.mayInterceptMessage(
                request,
                listOf(SseBreakpointProtocol.id),
                TrafficDirection.SERVER_TO_CLIENT,
            )
        ) return null
        return SseBreakpointTransformer(request, admittedCapture, scope, gate, limits)
    }
}

/** Response-only transformer that withholds at most one configured SSE record at a time. */
private class SseBreakpointTransformer(
    private val request: HttpRequestSnapshot,
    private val exchangeCapture: ProxyExchangeCapture,
    private val scope: CoroutineScope,
    private val gate: ProtocolMessageBreakpointGate,
    limits: SseLimits,
) : ProxyStreamTransformer {
    private val cancelled = AtomicBoolean(false)
    private val framer = SseBreakpointRecordFramer(limits.maximumEditableRecordBytes)
    private val codecs = SseContentCodecRegistry(limits)
    private var decoder: SseContentDecoder? = null
    private var encoder: SseContentEncoder? = null
    private var recognized = false
    private var transportBypass = false
    private var semanticBypass = false
    private var sequence = 0L

    override fun onResponse(response: ResponseHead, occurredAtEpochMillis: Long) {
        recognized = SseProtocol.isEventStream(response.headers)
        if (!recognized) return
        when (val result = codecs.resolve(SseProtocol.header(response.headers, SseProtocol.CONTENT_ENCODING))) {
            is SseContentCodecPlanResult.Supported -> {
                decoder = result.plan.openDecoder()
                encoder = result.plan.openEncoder()
            }
            is SseContentCodecPlanResult.Unavailable -> transportBypass = true
        }
    }

    override fun transform(
        direction: TrafficDirection,
        payload: ByteArray,
        endOfDirection: Boolean,
        occurredAtEpochMillis: Long,
    ): CompletionStage<ProxyStreamTransformResult> {
        if (cancelled.get()) {
            return CompletableFuture.completedFuture(ProxyStreamTransformResult.DropStream(STREAM_CANCELLED))
        }
        if (direction != TrafficDirection.SERVER_TO_CLIENT || !recognized || transportBypass) {
            return CompletableFuture.completedFuture(ProxyStreamTransformResult.Forward(payload))
        }
        val future = CompletableFuture<ProxyStreamTransformResult>()
        scope.launch {
            val result = runCatching {
                transformResponse(payload, endOfDirection, occurredAtEpochMillis)
            }.getOrElse { ProxyStreamTransformResult.DropStream(TRANSFORM_FAILED) }
            future.complete(result)
        }
        return future
    }

    override fun cancel(errorCode: String?) {
        if (!cancelled.compareAndSet(false, true)) return
        closeCodecs()
        framer.clear()
        gate.cancelProtocolMessages(exchangeCapture.exchangeId)
    }

    private suspend fun transformResponse(
        input: ByteArray,
        endOfDirection: Boolean,
        occurredAtEpochMillis: Long,
    ): ProxyStreamTransformResult {
        val decoded = when (val result = requireNotNull(decoder).accept(input, endOfDirection)) {
            is SseContentCodecResult.Failure -> {
                closeCodecs()
                return ProxyStreamTransformResult.DropStream(result.reason.code)
            }
            is SseContentCodecResult.Output -> result.copyBytes()
        }
        val transformed = transformDecodedResponse(decoded, endOfDirection, occurredAtEpochMillis)
        if (transformed is ProxyStreamTransformResult.DropStream) {
            closeCodecs()
            return transformed
        }
        val encoded = when (
            val result = requireNotNull(encoder).accept(
                (transformed as ProxyStreamTransformResult.Forward).payload,
                endOfDirection,
            )
        ) {
            is SseContentCodecResult.Failure -> {
                closeCodecs()
                return ProxyStreamTransformResult.DropStream(result.reason.code)
            }
            is SseContentCodecResult.Output -> result.copyBytes()
        }
        if (endOfDirection) closeCodecs()
        return ProxyStreamTransformResult.Forward(encoded)
    }

    private suspend fun transformDecodedResponse(
        input: ByteArray,
        endOfDirection: Boolean,
        occurredAtEpochMillis: Long,
    ): ProxyStreamTransformResult {
        if (semanticBypass) return ProxyStreamTransformResult.Forward(input)
        val output = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < input.size) {
            val completed = framer.accept(input[offset])
            offset++
            if (framer.overflowed) {
                output += framer.drain()
                if (offset < input.size) output += input.copyOfRange(offset, input.size)
                semanticBypass = true
                return ProxyStreamTransformResult.Forward(output.concatenate())
            }
            for (record in completed) {
                when (val resolved = resolveRecord(record, occurredAtEpochMillis)) {
                    is ProxyStreamTransformResult.Forward -> output += resolved.payload
                    is ProxyStreamTransformResult.DropStream -> return resolved
                }
            }
        }
        if (endOfDirection) {
            for (record in framer.finishRecords()) {
                when (val resolved = resolveRecord(record, occurredAtEpochMillis)) {
                    is ProxyStreamTransformResult.Forward -> output += resolved.payload
                    is ProxyStreamTransformResult.DropStream -> return resolved
                }
            }
            framer.drain().takeIf(ByteArray::isNotEmpty)?.let(output::add)
        }
        return ProxyStreamTransformResult.Forward(output.concatenate())
    }

    private fun closeCodecs() {
        decoder?.close()
        encoder?.close()
    }

    private suspend fun resolveRecord(
        original: ByteArray,
        occurredAtEpochMillis: Long,
    ): ProxyStreamTransformResult {
        val parsed = SseIncrementalParser().accept(original)
        if (parsed.singleOrNull() !is SseParseResult.Record) {
            return ProxyStreamTransformResult.Forward(original)
        }
        val decision = gate.interceptMessage(
            ProtocolMessageBreakpointCandidate(
                exchangeId = exchangeCapture.exchangeId,
                messageId = ProtocolMessageId(Uuid.random().toString()),
                protocolRoute = listOf(SseBreakpointProtocol.id),
                kind = ProtocolMessageKind.RECORD,
                request = request,
                direction = TrafficDirection.SERVER_TO_CLIENT,
                sequence = ++sequence,
                declaredBytes = original.size.toLong(),
                compressed = false,
                compressionEncoding = null,
                body = BreakpointBody(original),
                startedAtEpochMillis = occurredAtEpochMillis,
            ),
        )
        return when (decision) {
            ProtocolMessageBreakpointDecision.ContinueUnchanged -> ProxyStreamTransformResult.Forward(original)
            is ProtocolMessageBreakpointDecision.Replace ->
                ProxyStreamTransformResult.Forward(decision.body.copyBytes())
            ProtocolMessageBreakpointDecision.DropStream -> ProxyStreamTransformResult.DropStream(RECORD_DROPPED)
        }
    }

    private companion object {
        const val STREAM_CANCELLED: String = "sse_breakpoint_stream_cancelled"
        const val TRANSFORM_FAILED: String = "sse_breakpoint_transform_failed"
        const val RECORD_DROPPED: String = "sse_breakpoint_record_dropped"
    }
}

/** Fixed-capacity SSE delimiter framer used only on the opt-in breakpoint path. */
private class SseBreakpointRecordFramer(maximumRecordBytes: Int) {
    private val bytes = ByteArray(maximumRecordBytes + 1)
    private var count = 0
    private var lineBytes = 0
    private var pendingCarriageReturn = false
    private var pendingBlank = false
    var overflowed: Boolean = false
        private set

    fun accept(byte: Byte): List<ByteArray> {
        if (overflowed) return emptyList()
        val completed = mutableListOf<ByteArray>()
        if (pendingCarriageReturn) {
            pendingCarriageReturn = false
            if (byte == LF) {
                append(byte)
                if (pendingBlank) completed += completeRecord()
                pendingBlank = false
                return completed
            }
            if (pendingBlank) completed += completeRecord()
            pendingBlank = false
        }
        when (byte) {
            CR -> {
                append(byte)
                pendingCarriageReturn = true
                pendingBlank = lineBytes == 0
                lineBytes = 0
            }
            LF -> {
                append(byte)
                if (lineBytes == 0) completed += completeRecord() else lineBytes = 0
            }
            else -> {
                append(byte)
                lineBytes++
            }
        }
        return completed
    }

    fun finishRecords(): List<ByteArray> {
        if (!pendingCarriageReturn) return emptyList()
        pendingCarriageReturn = false
        val completed = if (pendingBlank) listOf(completeRecord()) else emptyList()
        pendingBlank = false
        return completed
    }

    fun drain(): ByteArray = bytes.copyOf(count).also { clear() }

    fun clear() {
        count = 0
        lineBytes = 0
        pendingCarriageReturn = false
        pendingBlank = false
        overflowed = false
    }

    private fun append(byte: Byte) {
        if (count == bytes.size) {
            overflowed = true
            return
        }
        bytes[count++] = byte
        if (count == bytes.size) overflowed = true
    }

    private fun completeRecord(): ByteArray = bytes.copyOf(count).also {
        count = 0
        lineBytes = 0
    }

    private companion object {
        const val CR: Byte = 13
        const val LF: Byte = 10
    }
}

private fun List<ByteArray>.concatenate(): ByteArray {
    if (isEmpty()) return ByteArray(0)
    val result = ByteArray(sumOf(ByteArray::size))
    var offset = 0
    forEach { chunk ->
        chunk.copyInto(result, offset)
        offset += chunk.size
    }
    return result
}
