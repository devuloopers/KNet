package com.devuloopers.knet.engine.grpc

import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyMessageCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyMessageCaptureMetadata
import com.devuloopers.knet.engine.proxy.inspection.ProxyPayloadSlice
import com.devuloopers.knet.engine.proxy.inspection.ProxyStreamInspector
import com.devuloopers.knet.engine.proxy.inspection.ProxyStreamInspectorFactory
import com.devuloopers.knet.traffic.id.ProtocolMessageId
import com.devuloopers.knet.traffic.id.StreamId
import com.devuloopers.knet.traffic.model.ExchangeTerminalOutcome
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficTerminationCode
import com.devuloopers.knet.traffic.model.TrafficTerminationReason
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.ResponseHead
import com.devuloopers.knet.traffic.model.http.StandardApplicationProtocol
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.message.MessageProtocolId
import com.devuloopers.knet.traffic.model.message.ProtocolMessageKind
import com.devuloopers.knet.traffic.model.message.ProtocolMessageState
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Creates a streaming native-gRPC observer without adding protobuf awareness to the proxy. */
class GrpcStreamInspectorFactory(
    private val maximumDeclaredMessageBytes: Long = DEFAULT_MAXIMUM_MESSAGE_BYTES,
) : ProxyStreamInspectorFactory {
    init {
        require(maximumDeclaredMessageBytes > 0L) { "Maximum gRPC message bytes must be positive." }
    }

    override fun create(
        request: RequestHead,
        streamId: StreamId?,
        capture: ProxyExchangeCapture?,
    ): ProxyStreamInspector? {
        if (request.method != HttpMethod.POST) return null
        val isHttpTwo = (request.protocol as? ApplicationProtocol.Standard)?.value ==
            StandardApplicationProtocol.HTTP_2
        if (!isHttpTwo) return null
        if (!GrpcProtocol.isNativeContentType(GrpcProtocol.header(request.headers, CONTENT_TYPE))) return null
        val method = GrpcMethodIdentity.fromTarget(request.target) ?: return null
        return GrpcStreamInspector(
            method = method,
            streamId = streamId,
            capture = capture,
            requestEncoding = GrpcProtocol.header(request.headers, GRPC_ENCODING),
            maximumDeclaredMessageBytes = maximumDeclaredMessageBytes,
        )
    }

    private companion object {
        const val DEFAULT_MAXIMUM_MESSAGE_BYTES: Long = 64L * 1024L * 1024L
        const val CONTENT_TYPE: String = "content-type"
        const val GRPC_ENCODING: String = "grpc-encoding"
    }
}

/** One exchange-scoped pair of incremental request and response gRPC deframers. */
private class GrpcStreamInspector(
    @Suppress("unused") private val method: GrpcMethodIdentity,
    private val streamId: StreamId?,
    private val capture: ProxyExchangeCapture?,
    requestEncoding: String?,
    maximumDeclaredMessageBytes: Long,
) : ProxyStreamInspector {
    private val requestDeframer = GrpcMessageDeframer(
        direction = TrafficDirection.CLIENT_TO_SERVER,
        streamId = streamId,
        compressionEncoding = requestEncoding,
        capture = capture,
        maximumDeclaredMessageBytes = maximumDeclaredMessageBytes,
    )
    private val responseDeframer = GrpcMessageDeframer(
        direction = TrafficDirection.SERVER_TO_CLIENT,
        streamId = streamId,
        compressionEncoding = null,
        capture = capture,
        maximumDeclaredMessageBytes = maximumDeclaredMessageBytes,
        enabled = false,
    )

    override fun onResponse(response: ResponseHead, occurredAtEpochMillis: Long) {
        responseDeframer.enabled = GrpcProtocol.isNativeContentType(
            GrpcProtocol.header(response.headers, CONTENT_TYPE),
        )
        responseDeframer.compressionEncoding = GrpcProtocol.header(response.headers, GRPC_ENCODING)
    }

    override fun onPayload(
        direction: TrafficDirection,
        payload: ProxyPayloadSlice,
        occurredAtEpochMillis: Long,
    ) {
        deframer(direction).accept(payload, occurredAtEpochMillis)
    }

    override fun onTrailers(
        direction: TrafficDirection,
        trailers: List<HeaderField>,
        occurredAtEpochMillis: Long,
    ) {
        if (direction == TrafficDirection.SERVER_TO_CLIENT) {
            responseDeframer.compressionEncoding =
                GrpcProtocol.header(trailers, GRPC_ENCODING) ?: responseDeframer.compressionEncoding
        }
    }

    override fun onDirectionEnd(direction: TrafficDirection, occurredAtEpochMillis: Long) {
        deframer(direction).finish(occurredAtEpochMillis, grpcTermination(MESSAGE_ENDED_MID_FRAME))
    }

    override fun onExchangeTerminated(
        outcome: ExchangeTerminalOutcome,
        occurredAtEpochMillis: Long,
    ) {
        val reason = outcome.reason ?: grpcTermination(PARENT_EXCHANGE_TERMINATED)
        requestDeframer.cancel(occurredAtEpochMillis, reason)
        responseDeframer.cancel(occurredAtEpochMillis, reason)
    }

    private fun deframer(direction: TrafficDirection): GrpcMessageDeframer = when (direction) {
        TrafficDirection.CLIENT_TO_SERVER -> requestDeframer
        TrafficDirection.SERVER_TO_CLIENT -> responseDeframer
    }

    private companion object {
        const val CONTENT_TYPE: String = "content-type"
        const val GRPC_ENCODING: String = "grpc-encoding"
        const val MESSAGE_ENDED_MID_FRAME: String = "grpc_message_ended_mid_frame"
        const val PARENT_EXCHANGE_TERMINATED: String = "grpc_parent_exchange_terminated"
    }
}

/** Bounded five-byte gRPC envelope parser that streams payload directly into capture reservations. */
@OptIn(ExperimentalUuidApi::class)
private class GrpcMessageDeframer(
    private val direction: TrafficDirection,
    private val streamId: StreamId?,
    compressionEncoding: String?,
    private val capture: ProxyExchangeCapture?,
    private val maximumDeclaredMessageBytes: Long,
    enabled: Boolean = true,
) {
    var compressionEncoding: String? = compressionEncoding
    var enabled: Boolean = enabled
    private val prefix = ByteArray(PREFIX_BYTES)
    private var prefixBytes: Int = 0
    private var remainingPayloadBytes: Long = 0L
    private var observedPayloadBytes: Long = 0L
    private var sequence: Long = 0L
    private var messageCapture: ProxyMessageCapture? = null
    private var failed: Boolean = false

    fun accept(payload: ProxyPayloadSlice, occurredAtEpochMillis: Long) {
        if (!enabled || failed || payload.size == 0) return
        var offset = 0
        while (offset < payload.size && !failed) {
            if (prefixBytes < PREFIX_BYTES) {
                val copied = minOf(PREFIX_BYTES - prefixBytes, payload.size - offset)
                payload.copyTo(prefix, prefixBytes, offset, copied)
                prefixBytes += copied
                offset += copied
                if (prefixBytes < PREFIX_BYTES) continue
                startMessage(occurredAtEpochMillis)
                if (failed) return
                if (remainingPayloadBytes == 0L) finishCurrent(occurredAtEpochMillis)
                continue
            }

            val available = minOf(
                remainingPayloadBytes,
                (payload.size - offset).toLong(),
            ).toInt()
            if (available == 0) continue
            val reservation = messageCapture?.tryReservePayload(available)
            if (reservation == null) {
                offset += available
                observedPayloadBytes += available
                remainingPayloadBytes -= available
            } else {
                val captured = reservation.writableBytes.size
                payload.copyTo(reservation.writableBytes, sourceOffset = offset, length = captured)
                reservation.publish(occurredAtEpochMillis)
                offset += captured
                observedPayloadBytes += captured
                remainingPayloadBytes -= captured
            }
            if (remainingPayloadBytes == 0L) finishCurrent(occurredAtEpochMillis)
        }
    }

    fun finish(occurredAtEpochMillis: Long, reason: TrafficTerminationReason) {
        if (!enabled || failed) return
        if (prefixBytes == 0 && remainingPayloadBytes == 0L) return
        messageCapture?.terminate(
            observedBytes = observedPayloadBytes,
            state = ProtocolMessageState.FAILED,
            occurredAtEpochMillis = occurredAtEpochMillis,
            reason = reason,
        )
        reset()
        failed = true
    }

    fun cancel(occurredAtEpochMillis: Long, reason: TrafficTerminationReason) {
        if (!enabled || failed) return
        if (messageCapture != null || prefixBytes > 0) {
            messageCapture?.terminate(
                observedBytes = observedPayloadBytes,
                state = ProtocolMessageState.CANCELLED,
                occurredAtEpochMillis = occurredAtEpochMillis,
                reason = reason,
            )
        }
        reset()
        failed = true
    }

    private fun startMessage(occurredAtEpochMillis: Long) {
        val flag = prefix[0].toInt() and 0xff
        val declared = ((prefix[1].toLong() and 0xffL) shl 24) or
            ((prefix[2].toLong() and 0xffL) shl 16) or
            ((prefix[3].toLong() and 0xffL) shl 8) or
            (prefix[4].toLong() and 0xffL)
        val compressed = flag == COMPRESSED_FLAG
        val metadata = ProxyMessageCaptureMetadata(
            messageId = ProtocolMessageId(Uuid.random().toString()),
            streamId = streamId,
            protocol = MessageProtocolId.GRPC,
            kind = ProtocolMessageKind.DATA,
            direction = direction,
            messageSequence = ++sequence,
            declaredBytes = declared,
            compressed = compressed,
            compressionEncoding = compressionEncoding,
            occurredAtEpochMillis = occurredAtEpochMillis,
        )
        messageCapture = capture?.startMessage(metadata)
        remainingPayloadBytes = declared
        observedPayloadBytes = 0L
        if (flag != UNCOMPRESSED_FLAG && flag != COMPRESSED_FLAG) {
            failCurrent(occurredAtEpochMillis, grpcTermination(INVALID_COMPRESSION_FLAG))
        } else if (declared > maximumDeclaredMessageBytes) {
            failCurrent(occurredAtEpochMillis, grpcTermination(DECLARED_LENGTH_LIMIT))
        } else if (compressed && compressionEncoding.isNullOrBlank()) {
            failCurrent(occurredAtEpochMillis, grpcTermination(MISSING_COMPRESSION_ENCODING))
        }
    }

    private fun finishCurrent(occurredAtEpochMillis: Long) {
        messageCapture?.complete(observedPayloadBytes, occurredAtEpochMillis)
        reset()
    }

    private fun failCurrent(occurredAtEpochMillis: Long, reason: TrafficTerminationReason) {
        messageCapture?.terminate(
            observedBytes = observedPayloadBytes,
            state = ProtocolMessageState.FAILED,
            occurredAtEpochMillis = occurredAtEpochMillis,
            reason = reason,
        )
        reset()
        failed = true
    }

    private fun reset() {
        prefixBytes = 0
        remainingPayloadBytes = 0L
        observedPayloadBytes = 0L
        messageCapture = null
    }

    private companion object {
        const val PREFIX_BYTES: Int = 5
        const val UNCOMPRESSED_FLAG: Int = 0
        const val COMPRESSED_FLAG: Int = 1
        const val INVALID_COMPRESSION_FLAG: String = "grpc_invalid_compression_flag"
        const val DECLARED_LENGTH_LIMIT: String = "grpc_declared_length_limit"
        const val MISSING_COMPRESSION_ENCODING: String = "grpc_missing_compression_encoding"
    }
}

private fun grpcTermination(code: String): TrafficTerminationReason = TrafficTerminationReason.Protocol(
    protocol = MessageProtocolId.GRPC,
    code = TrafficTerminationCode(code),
)
