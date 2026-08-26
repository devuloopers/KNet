package com.devuloopers.knet.engine.grpc

import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolExecutionCommand
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolExecutionEvent
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolExecutor
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolExecutionSession
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolMessage
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolMessageDirection
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolRoute
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolSessionExecutor
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import io.grpc.CallOptions
import io.grpc.ClientCall
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Native grpc-java execution adapter for opaque API Studio protocol documents.
 *
 * One low-level [ClientCall] path handles all cardinalities, applies inbound demand one message at
 * a time, and drains outbound messages only while the transport reports readiness.
 */
class GrpcApiStudioExecutor(
    private val descriptors: GrpcDescriptorRegistry,
    private val draftCodec: GrpcRequestDraftCodec,
    private val channels: GrpcClientChannelFactory,
) : ApiStudioProtocolExecutor, ApiStudioProtocolSessionExecutor {
    override val kind: RequestKindId = RequestKindId.GRPC

    override fun open(
        command: ApiStudioProtocolExecutionCommand,
    ): Result<ApiStudioProtocolExecutionSession> = runCatching {
        val draft = draftCodec.decode(command.document).getOrThrow()
        val schema = requireNotNull(descriptors.resolve(draft.method)) {
            "No descriptor is loaded for ${draft.method.path}."
        }
        require(schema.toCallShape() == draft.callShape) {
            "${draft.method.path} descriptor cardinality does not match the saved draft."
        }
        require(draft.callShape.acceptsMultipleOutboundMessages) {
            "Interactive execution requires a client-streaming or bidirectional method."
        }
        GrpcInteractiveExecutionSession(
            descriptors = descriptors,
            draft = draft,
            channel = channels.create(draft.targetHost, draft.targetPort, draft.useTls, command.route),
        )
    }

    override fun execute(command: ApiStudioProtocolExecutionCommand): Flow<ApiStudioProtocolExecutionEvent> =
        callbackFlow {
            val draft = draftCodec.decode(command.document).getOrElse { error ->
                trySend(ApiStudioProtocolExecutionEvent.Failed(
                    code = "invalid_grpc_document",
                    message = error.message ?: "The gRPC document is invalid.",
                    retryable = false,
                ))
                close()
                return@callbackFlow
            }
            val schema = descriptors.resolve(draft.method)
            if (schema == null) {
                trySend(ApiStudioProtocolExecutionEvent.Failed(
                    code = "grpc_descriptor_not_found",
                    message = "No descriptor is loaded for ${draft.method.path}.",
                    retryable = false,
                ))
                close()
                return@callbackFlow
            }
            val declaredShape = schema.toCallShape()
            if (declaredShape != draft.callShape) {
                trySend(ApiStudioProtocolExecutionEvent.Failed(
                    code = "grpc_call_shape_mismatch",
                    message = "${draft.method.path} is $declaredShape, not ${draft.callShape}.",
                    retryable = false,
                ))
                close()
                return@callbackFlow
            }
            val outboundPayloads = draft.outboundMessagesJson.mapIndexed { index, authoredJson ->
                descriptors.encode(draft.method, GrpcPayloadDirection.REQUEST, authoredJson)
                    .getOrElse { error ->
                        trySend(ApiStudioProtocolExecutionEvent.Failed(
                            code = "grpc_request_encode_failed",
                            message = "Message ${index + 1}: ${error.message ?: "invalid protobuf JSON"}",
                            retryable = false,
                        ))
                        close()
                        return@callbackFlow
                    }
            }
            if (!draft.callShape.acceptsMultipleOutboundMessages && outboundPayloads.size != 1) {
                trySend(ApiStudioProtocolExecutionEvent.Failed(
                    code = "grpc_outbound_cardinality_invalid",
                    message = "${draft.callShape} requires exactly one outbound message.",
                    retryable = false,
                ))
                close()
                return@callbackFlow
            }

            val channel = runCatching {
                channels.create(draft.targetHost, draft.targetPort, draft.useTls, command.route)
            }.getOrElse { error ->
                trySend(ApiStudioProtocolExecutionEvent.Failed(
                    code = "grpc_channel_failed",
                    message = error.message ?: "Unable to create the gRPC channel.",
                    retryable = false,
                ))
                close()
                return@callbackFlow
            }
            val method = MethodDescriptor.newBuilder<ByteArray, ByteArray>()
                .setType(draft.callShape.methodType)
                .setFullMethodName(
                    MethodDescriptor.generateFullMethodName(
                        draft.method.serviceName,
                        draft.method.methodName,
                    ),
                )
                .setRequestMarshaller(ByteArrayMarshaller)
                .setResponseMarshaller(ByteArrayMarshaller)
                .build()
            val call = channel.newCall(
                method,
                CallOptions.DEFAULT.withDeadlineAfter(draft.deadlineMillis, TimeUnit.MILLISECONDS),
            )
            val sequence = AtomicLong(0L)
            val terminal = AtomicBoolean(false)
            val outboundQueue = ArrayDeque(outboundPayloads)
            val drainLock = Any()
            var halfClosed = false

            fun publishMessage(direction: ApiStudioProtocolMessageDirection, payload: ByteArray) {
                val payloadDirection = when (direction) {
                    ApiStudioProtocolMessageDirection.OUTBOUND -> GrpcPayloadDirection.REQUEST
                    ApiStudioProtocolMessageDirection.INBOUND -> GrpcPayloadDirection.RESPONSE
                }
                val displayText = when (val decoded = descriptors.decode(
                    draft.method,
                    payloadDirection,
                    payload,
                )) {
                    is GrpcPayloadDecodeResult.DecodedJson -> decoded.json
                    is GrpcPayloadDecodeResult.Unavailable -> payload.toHexText()
                }
                trySend(ApiStudioProtocolExecutionEvent.Message(
                    ApiStudioProtocolMessage(
                        sequence = sequence.incrementAndGet(),
                        direction = direction,
                        contentType = if (displayText.startsWith('{') || displayText.startsWith('[')) {
                            "application/json"
                        } else {
                            "application/octet-stream"
                        },
                        displayText = displayText,
                        payload = payload,
                    ),
                ))
            }

            fun drainOutbound(allowInitialMessage: Boolean = false) {
                synchronized(drainLock) {
                    if (terminal.get()) return
                    if (allowInitialMessage && outboundQueue.isNotEmpty()) {
                        val payload = outboundQueue.removeFirst()
                        call.sendMessage(payload)
                        publishMessage(ApiStudioProtocolMessageDirection.OUTBOUND, payload)
                    }
                    while (call.isReady && outboundQueue.isNotEmpty()) {
                        val payload = outboundQueue.removeFirst()
                        call.sendMessage(payload)
                        publishMessage(ApiStudioProtocolMessageDirection.OUTBOUND, payload)
                    }
                    if (outboundQueue.isEmpty() && !halfClosed) {
                        halfClosed = true
                        call.halfClose()
                    }
                }
            }

            trySend(ApiStudioProtocolExecutionEvent.Started(
                "${draft.callShape} ${draft.targetHost}:${draft.targetPort}${draft.method.path}",
            ))
            call.start(
                object : ClientCall.Listener<ByteArray>() {
                    override fun onMessage(message: ByteArray) {
                        publishMessage(ApiStudioProtocolMessageDirection.INBOUND, message)
                        call.request(1)
                    }

                    override fun onReady() {
                        drainOutbound()
                    }

                    override fun onClose(status: Status, trailers: Metadata) {
                        if (!terminal.compareAndSet(false, true)) return
                        if (status.isOk) {
                            trySend(ApiStudioProtocolExecutionEvent.Completed(
                                statusCode = status.code.name,
                                statusMessage = status.description,
                                actualProtocol = "HTTP/2",
                                trailers = trailers.presentationEntries(),
                            ))
                        } else {
                            trySend(ApiStudioProtocolExecutionEvent.Failed(
                                code = status.code.name,
                                message = status.description ?: status.code.name,
                                retryable = status.code.isRetryable,
                                actualProtocol = "HTTP/2",
                                trailers = trailers.presentationEntries(),
                            ))
                        }
                        close()
                    }
                },
                draft.metadata.toGrpcMetadata(),
            )
            call.request(1)
            // isReady is an advisory high-watermark signal and some transports do not emit an
            // initial false-to-true callback. One bounded message is therefore allowed to prime
            // the stream; every subsequent message remains readiness-gated.
            drainOutbound(allowInitialMessage = true)

            awaitClose {
                if (terminal.compareAndSet(false, true)) {
                    call.cancel("API Studio execution cancelled", null)
                }
                channel.shutdownNow()
            }
        }

}

internal val GrpcCallShape.acceptsMultipleOutboundMessages: Boolean
    get() = this == GrpcCallShape.CLIENT_STREAMING || this == GrpcCallShape.BIDIRECTIONAL_STREAMING

internal val GrpcCallShape.methodType: MethodDescriptor.MethodType
    get() = when (this) {
        GrpcCallShape.UNARY -> MethodDescriptor.MethodType.UNARY
        GrpcCallShape.SERVER_STREAMING -> MethodDescriptor.MethodType.SERVER_STREAMING
        GrpcCallShape.CLIENT_STREAMING -> MethodDescriptor.MethodType.CLIENT_STREAMING
        GrpcCallShape.BIDIRECTIONAL_STREAMING -> MethodDescriptor.MethodType.BIDI_STREAMING
    }

internal fun GrpcMethodSchema.toCallShape(): GrpcCallShape = when {
    clientStreaming && serverStreaming -> GrpcCallShape.BIDIRECTIONAL_STREAMING
    clientStreaming -> GrpcCallShape.CLIENT_STREAMING
    serverStreaming -> GrpcCallShape.SERVER_STREAMING
    else -> GrpcCallShape.UNARY
}

@OptIn(ExperimentalEncodingApi::class)
internal fun List<GrpcMetadataEntry>.toGrpcMetadata(): Metadata = Metadata().also { metadata ->
    asSequence().filter(GrpcMetadataEntry::enabled).forEach { entry ->
        if (entry.name.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
            metadata.put(
                Metadata.Key.of(entry.name, Metadata.BINARY_BYTE_MARSHALLER),
                Base64.decode(entry.value),
            )
        } else {
            metadata.put(Metadata.Key.of(entry.name, Metadata.ASCII_STRING_MARSHALLER), entry.value)
        }
    }
}

internal fun Metadata.presentationEntries(): List<Pair<String, String>> = keys()
    .sorted()
    .mapNotNull { name ->
        if (name.endsWith(Metadata.BINARY_HEADER_SUFFIX)) return@mapNotNull name to "<binary>"
        get(Metadata.Key.of(name, Metadata.ASCII_STRING_MARSHALLER))?.let { value -> name to value }
    }

internal val Status.Code.isRetryable: Boolean
    get() = this == Status.Code.UNAVAILABLE ||
        this == Status.Code.RESOURCE_EXHAUSTED ||
        this == Status.Code.ABORTED

internal object ByteArrayMarshaller : MethodDescriptor.Marshaller<ByteArray> {
    override fun stream(value: ByteArray): InputStream = ByteArrayInputStream(value)
    override fun parse(stream: InputStream): ByteArray = stream.use(InputStream::readBytes)
}

internal fun ByteArray.toHexText(): String {
    val digits = "0123456789ABCDEF"
    return joinToString(" ") { byte ->
        val value = byte.toInt() and 0xFF
        "${digits[value ushr 4]}${digits[value and 0x0F]}"
    }
}
