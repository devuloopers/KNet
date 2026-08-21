package com.devuloopers.knet.testingserver.grpc

import com.devuloopers.knet.testingserver.grpc.v1.EchoReply
import com.devuloopers.knet.testingserver.grpc.v1.EchoRequest
import com.devuloopers.knet.testingserver.grpc.v1.FailureRequest
import com.devuloopers.knet.testingserver.grpc.v1.ProtocolLabGrpc
import com.devuloopers.knet.testingserver.grpc.v1.StreamRequest
import com.devuloopers.knet.testingserver.grpc.v1.StreamSummary
import io.grpc.Metadata
import io.grpc.Status
import io.grpc.stub.StreamObserver
import org.springframework.stereotype.Component

/** Implements deterministic native gRPC scenarios across every standard RPC cardinality. */
@Component
class ProtocolLabGrpcService : ProtocolLabGrpc.ProtocolLabImplBase() {
    /**
     * Returns one response for one request.
     *
     * @param request Incoming echo request.
     * @param responseObserver Observer receiving one response and completion.
     */
    override fun unaryEcho(request: EchoRequest, responseObserver: StreamObserver<EchoReply>) {
        responseObserver.onNext(reply(request.message, sequence = 1))
        responseObserver.onCompleted()
    }

    /**
     * Emits a bounded ordered response stream for one request.
     *
     * @param request Stream prefix and requested message count.
     * @param responseObserver Observer receiving every ordered response.
     */
    override fun serverStream(request: StreamRequest, responseObserver: StreamObserver<EchoReply>) {
        val count = request.count.coerceIn(1, MAX_STREAM_MESSAGES)
        repeat(count) { index ->
            responseObserver.onNext(reply(request.message, sequence = index + 1))
        }
        responseObserver.onCompleted()
    }

    /**
     * Collects a client stream and returns one summary after client completion.
     *
     * grpc-java serializes callbacks for an individual call, so the list remains confined to this observer.
     *
     * @param responseObserver Observer receiving the final stream summary.
     * @return Observer accepting the client request stream.
     */
    override fun clientStream(responseObserver: StreamObserver<StreamSummary>): StreamObserver<EchoRequest> {
        val messages = mutableListOf<String>()
        return object : StreamObserver<EchoRequest> {
            override fun onNext(request: EchoRequest) {
                if (messages.size < MAX_STREAM_MESSAGES) {
                    messages += request.message
                }
            }

            override fun onError(throwable: Throwable) {
                messages.clear()
            }

            override fun onCompleted() {
                responseObserver.onNext(
                    StreamSummary.newBuilder()
                        .setReceivedCount(messages.size)
                        .addAllMessages(messages)
                        .build(),
                )
                responseObserver.onCompleted()
            }
        }
    }

    /**
     * Echoes each client message immediately over an independent bidirectional stream.
     *
     * @param responseObserver Observer receiving ordered echo responses.
     * @return Observer accepting client messages until completion or cancellation.
     */
    override fun bidirectionalEcho(responseObserver: StreamObserver<EchoReply>): StreamObserver<EchoRequest> {
        var sequence = 0
        return object : StreamObserver<EchoRequest> {
            override fun onNext(request: EchoRequest) {
                if (sequence >= MAX_STREAM_MESSAGES) return
                sequence += 1
                responseObserver.onNext(reply(request.message, sequence))
            }

            override fun onError(throwable: Throwable) = Unit

            override fun onCompleted() {
                responseObserver.onCompleted()
            }
        }
    }

    /**
     * Terminates an RPC with a typed status and custom trailer for error-path inspection.
     *
     * @param request Requested failure description.
     * @param responseObserver Observer receiving the terminal error.
     */
    override fun fail(request: FailureRequest, responseObserver: StreamObserver<EchoReply>) {
        val trailers = Metadata().apply {
            put(TEST_TRAILER_KEY, "protocol-lab-failure")
        }
        val description = request.description.ifBlank { "Requested protocol-lab failure" }
        responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(description).asRuntimeException(trailers))
    }

    private fun reply(message: String, sequence: Int): EchoReply = EchoReply.newBuilder()
        .setMessage(message)
        .setSequence(sequence)
        .build()

    private companion object {
        const val MAX_STREAM_MESSAGES = 100
        val TEST_TRAILER_KEY: Metadata.Key<String> = Metadata.Key.of(
            "knet-test-trailer",
            Metadata.ASCII_STRING_MARSHALLER,
        )
    }
}
