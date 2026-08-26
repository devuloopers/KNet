package com.devuloopers.knet.engine.grpc

import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolExecutionEvent
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolExecutionSession
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolMessage
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolMessageDirection
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolOutboundMessage
import io.grpc.CallOptions
import io.grpc.ClientCall
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** Bounded, backpressure-aware live client-streaming or bidirectional grpc-java call. */
internal class GrpcInteractiveExecutionSession(
    private val descriptors: GrpcDescriptorRegistry,
    private val draft: GrpcRequestDraft,
    private val channel: ManagedChannel,
) : ApiStudioProtocolExecutionSession {
    private val eventChannel = Channel<ApiStudioProtocolExecutionEvent>(EVENT_CAPACITY)
    private val readiness = Channel<Unit>(Channel.CONFLATED)
    private val terminal = AtomicBoolean(false)
    private val sequence = AtomicLong(0L)
    private val mutationLock = Any()
    private var halfClosed = false
    private var outboundMessageCount = 0

    private val call: ClientCall<ByteArray, ByteArray> = channel.newCall(
        MethodDescriptor.newBuilder<ByteArray, ByteArray>()
            .setType(draft.callShape.methodType)
            .setFullMethodName(
                MethodDescriptor.generateFullMethodName(
                    draft.method.serviceName,
                    draft.method.methodName,
                ),
            )
            .setRequestMarshaller(ByteArrayMarshaller)
            .setResponseMarshaller(ByteArrayMarshaller)
            .build(),
        CallOptions.DEFAULT.withDeadlineAfter(draft.deadlineMillis, TimeUnit.MILLISECONDS),
    )

    override val events: Flow<ApiStudioProtocolExecutionEvent> = eventChannel.receiveAsFlow()

    init {
        require(draft.callShape.acceptsMultipleOutboundMessages) {
            "Interactive gRPC sessions require client-streaming cardinality."
        }
        publish(
            ApiStudioProtocolExecutionEvent.Started(
                "${draft.callShape} ${draft.targetHost}:${draft.targetPort}${draft.method.path}",
            ),
        )
        call.start(
            object : ClientCall.Listener<ByteArray>() {
                override fun onMessage(message: ByteArray) {
                    if (publishMessage(ApiStudioProtocolMessageDirection.INBOUND, message)) {
                        call.request(1)
                    }
                }

                override fun onReady() {
                    readiness.trySend(Unit)
                }

                override fun onClose(status: Status, trailers: Metadata) {
                    finish(status, trailers)
                }
            },
            draft.metadata.toGrpcMetadata(),
        )
        call.request(1)
        if (call.isReady) readiness.trySend(Unit)
    }

    override suspend fun send(message: ApiStudioProtocolOutboundMessage): Result<Unit> {
        val payload = descriptors.encode(draft.method, GrpcPayloadDirection.REQUEST, message.displayText)
            .getOrElse { return Result.failure(it) }
        return try {
            var sent = false
            while (!sent) {
                sent = synchronized(mutationLock) {
                    check(!terminal.get()) { "The gRPC stream is closed." }
                    check(!halfClosed) { "The gRPC request stream is already half-closed." }
                    check(outboundMessageCount < GrpcRequestDraft.MAXIMUM_OUTBOUND_MESSAGES) {
                        "The outbound gRPC message limit was reached."
                    }
                    if (!call.isReady) {
                        false
                    } else {
                        call.sendMessage(payload)
                        outboundMessageCount += 1
                        true
                    }
                }
                if (!sent) readiness.receive()
            }
            check(publishMessage(ApiStudioProtocolMessageDirection.OUTBOUND, payload)) {
                "The gRPC event buffer is full."
            }
            Result.success(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    override suspend fun halfClose(): Result<Unit> = try {
        synchronized(mutationLock) {
            check(!terminal.get()) { "The gRPC stream is closed." }
            check(!halfClosed) { "The gRPC request stream is already half-closed." }
            halfClosed = true
            call.halfClose()
        }
        Result.success(Unit)
    } catch (error: Exception) {
        Result.failure(error)
    }

    override fun cancel() {
        if (!terminal.compareAndSet(false, true)) return
        call.cancel("API Studio execution cancelled", null)
        publishTerminal(
            ApiStudioProtocolExecutionEvent.Failed(
                code = Status.Code.CANCELLED.name,
                message = "API Studio execution cancelled.",
                retryable = false,
                actualProtocol = "HTTP/2",
            ),
        )
    }

    private fun publishMessage(
        direction: ApiStudioProtocolMessageDirection,
        payload: ByteArray,
    ): Boolean {
        val payloadDirection = when (direction) {
            ApiStudioProtocolMessageDirection.OUTBOUND -> GrpcPayloadDirection.REQUEST
            ApiStudioProtocolMessageDirection.INBOUND -> GrpcPayloadDirection.RESPONSE
        }
        val displayText = when (val decoded = descriptors.decode(draft.method, payloadDirection, payload)) {
            is GrpcPayloadDecodeResult.DecodedJson -> decoded.json
            is GrpcPayloadDecodeResult.Unavailable -> payload.toHexText()
        }
        return publish(
            ApiStudioProtocolExecutionEvent.Message(
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
            ),
        )
    }

    private fun publish(event: ApiStudioProtocolExecutionEvent): Boolean {
        val result = eventChannel.trySend(event)
        if (result.isSuccess) return true
        if (terminal.compareAndSet(false, true)) {
            call.cancel("API Studio event buffer exhausted", null)
            eventChannel.close(IllegalStateException("The bounded gRPC event buffer is full."))
            readiness.close()
            channel.shutdownNow()
        }
        return false
    }

    private fun finish(status: Status, trailers: Metadata) {
        if (!terminal.compareAndSet(false, true)) return
        val event = if (status.isOk) {
            ApiStudioProtocolExecutionEvent.Completed(
                statusCode = status.code.name,
                statusMessage = status.description,
                actualProtocol = "HTTP/2",
                trailers = trailers.presentationEntries(),
            )
        } else {
            ApiStudioProtocolExecutionEvent.Failed(
                code = status.code.name,
                message = status.description ?: status.code.name,
                retryable = status.code.isRetryable,
                actualProtocol = "HTTP/2",
                trailers = trailers.presentationEntries(),
            )
        }
        publishTerminal(event)
    }

    private fun publishTerminal(event: ApiStudioProtocolExecutionEvent) {
        eventChannel.trySend(event)
        eventChannel.close()
        readiness.close()
        channel.shutdownNow()
    }

    private companion object {
        const val EVENT_CAPACITY = 256
    }
}
