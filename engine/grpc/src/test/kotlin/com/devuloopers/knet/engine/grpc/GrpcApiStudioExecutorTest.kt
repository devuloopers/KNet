package com.devuloopers.knet.engine.grpc

import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolExecutionCommand
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolExecutionEvent
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolOutboundMessage
import com.google.protobuf.DescriptorProtos
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Server
import io.grpc.ServerServiceDefinition
import io.grpc.Status
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import io.grpc.stub.ServerCalls
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GrpcApiStudioExecutorTest {
    private var server: Server? = null

    @AfterTest
    fun tearDown() {
        server?.shutdownNow()
    }

    @Test
    fun `codec round trips the extension owned draft`() {
        val codec = GrpcRequestDraftCodec()
        val draft = draft(GrpcCallShape.BIDIRECTIONAL_STREAMING, listOf("{\"value\":\"one\"}", "{\"value\":\"two\"}"))

        val decoded = codec.decode(codec.encode("draft-1", "Chat", draft)).getOrThrow()

        assertEquals(draft, decoded)
    }

    @Test
    fun `native executor supports every grpc call cardinality with bounded event flow`() = runBlocking {
        val registry = GrpcDescriptorRegistry()
        registry.importDescriptorSet(GrpcDescriptorSourceId("lab"), descriptorSet().toByteArray()).getOrThrow()
        server = NettyServerBuilder.forPort(0).addService(testService()).build().start()
        val codec = GrpcRequestDraftCodec()
        val executor = GrpcApiStudioExecutor(
            registry,
            codec,
            GrpcClientChannelFactory(byteArrayOf(1)),
        )

        GrpcCallShape.entries.forEach { shape ->
            val messages = if (shape == GrpcCallShape.CLIENT_STREAMING || shape == GrpcCallShape.BIDIRECTIONAL_STREAMING) {
                listOf("{\"value\":\"one\"}", "{\"value\":\"two\"}")
            } else {
                listOf("{\"value\":\"one\"}")
            }
            val document = codec.encode("draft-$shape", shape.name, draft(shape, messages))
            val events = try {
                withTimeout(5_000L) {
                    executor.execute(ApiStudioProtocolExecutionCommand(document)).toList()
                }
            } catch (error: Throwable) {
                throw AssertionError("$shape did not complete", error)
            }

            assertIs<ApiStudioProtocolExecutionEvent.Started>(events.first())
            assertIs<ApiStudioProtocolExecutionEvent.Completed>(events.last())
            val messageEvents = events.filterIsInstance<ApiStudioProtocolExecutionEvent.Message>()
            assertEquals(messages.size, messageEvents.count { it.message.direction.name == "OUTBOUND" })
            val expectedInbound = when (shape) {
                GrpcCallShape.SERVER_STREAMING -> 2
                GrpcCallShape.BIDIRECTIONAL_STREAMING -> messages.size
                GrpcCallShape.UNARY,
                GrpcCallShape.CLIENT_STREAMING -> 1
            }
            assertEquals(expectedInbound, messageEvents.count { it.message.direction.name == "INBOUND" })
            assertTrue(messageEvents.all { it.message.displayText.contains("value") })
        }
    }

    @Test
    fun `interactive executor sends client messages incrementally and half closes`() = runBlocking {
        val registry = GrpcDescriptorRegistry()
        registry.importDescriptorSet(GrpcDescriptorSourceId("lab"), descriptorSet().toByteArray()).getOrThrow()
        server = NettyServerBuilder.forPort(0).addService(testService()).build().start()
        val codec = GrpcRequestDraftCodec()
        val executor = GrpcApiStudioExecutor(
            registry,
            codec,
            GrpcClientChannelFactory(byteArrayOf(1)),
        )

        listOf(GrpcCallShape.CLIENT_STREAMING, GrpcCallShape.BIDIRECTIONAL_STREAMING).forEach { shape ->
            val document = codec.encode("live-$shape", shape.name, draft(shape, listOf("{}")))
            val session = executor.open(ApiStudioProtocolExecutionCommand(document)).getOrThrow()
            val events = coroutineScope {
                val collected = async {
                    withTimeout(5_000L) { session.events.toList() }
                }
                session.send(ApiStudioProtocolOutboundMessage("{\"value\":\"one\"}")).getOrThrow()
                session.send(ApiStudioProtocolOutboundMessage("{\"value\":\"two\"}")).getOrThrow()
                session.halfClose().getOrThrow()
                collected.await()
            }

            assertIs<ApiStudioProtocolExecutionEvent.Started>(events.first())
            assertIs<ApiStudioProtocolExecutionEvent.Completed>(events.last())
            val messages = events.filterIsInstance<ApiStudioProtocolExecutionEvent.Message>()
            assertEquals(2, messages.count { it.message.direction.name == "OUTBOUND" })
            val expectedInbound = if (shape == GrpcCallShape.BIDIRECTIONAL_STREAMING) 2 else 1
            assertEquals(expectedInbound, messages.count { it.message.direction.name == "INBOUND" })
        }
    }

    @Test
    fun `executor preserves grpc failure trailers and deadline protocol evidence`() = runBlocking {
        val registry = GrpcDescriptorRegistry()
        registry.importDescriptorSet(GrpcDescriptorSourceId("lab"), descriptorSet().toByteArray()).getOrThrow()
        server = NettyServerBuilder.forPort(0).addService(testService()).build().start()
        val codec = GrpcRequestDraftCodec()
        val executor = GrpcApiStudioExecutor(registry, codec, GrpcClientChannelFactory(byteArrayOf(1)))

        val failedEvents = withTimeout(5_000L) {
            executor.execute(
                ApiStudioProtocolExecutionCommand(
                    codec.encode(
                        "failed-call",
                        "Fail",
                        draft(GrpcCallShape.UNARY, listOf("{\"value\":\"one\"}"), methodName = "Fail"),
                    ),
                ),
            ).toList()
        }
        val failed = assertIs<ApiStudioProtocolExecutionEvent.Failed>(failedEvents.last())
        assertEquals(Status.Code.INVALID_ARGUMENT.name, failed.code)
        assertEquals("HTTP/2", failed.actualProtocol)
        assertTrue(("knet-test-trailer" to "preserved") in failed.trailers)

        val deadlineEvents = withTimeout(5_000L) {
            executor.execute(
                ApiStudioProtocolExecutionCommand(
                    codec.encode(
                        "deadline-call",
                        "Never",
                        draft(
                            GrpcCallShape.UNARY,
                            listOf("{\"value\":\"one\"}"),
                            methodName = "Never",
                            deadlineMillis = 25L,
                        ),
                    ),
                ),
            ).toList()
        }
        val deadline = assertIs<ApiStudioProtocolExecutionEvent.Failed>(deadlineEvents.last())
        assertEquals(Status.Code.DEADLINE_EXCEEDED.name, deadline.code)
        assertEquals("HTTP/2", deadline.actualProtocol)
    }

    @Test
    fun `interactive cancellation emits one typed terminal event`() = runBlocking {
        val registry = GrpcDescriptorRegistry()
        registry.importDescriptorSet(GrpcDescriptorSourceId("lab"), descriptorSet().toByteArray()).getOrThrow()
        server = NettyServerBuilder.forPort(0).addService(testService()).build().start()
        val codec = GrpcRequestDraftCodec()
        val executor = GrpcApiStudioExecutor(registry, codec, GrpcClientChannelFactory(byteArrayOf(1)))
        val session = executor.open(
            ApiStudioProtocolExecutionCommand(
                codec.encode(
                    "cancel-call",
                    "Bidi",
                    draft(GrpcCallShape.BIDIRECTIONAL_STREAMING, listOf("{}")),
                ),
            ),
        ).getOrThrow()

        val events = coroutineScope {
            val collected = async { withTimeout(5_000L) { session.events.toList() } }
            session.cancel()
            collected.await()
        }

        val terminal = assertIs<ApiStudioProtocolExecutionEvent.Failed>(events.last())
        assertEquals(Status.Code.CANCELLED.name, terminal.code)
        assertEquals("HTTP/2", terminal.actualProtocol)
        assertEquals(1, events.count { it is ApiStudioProtocolExecutionEvent.Failed })
    }

    private fun draft(
        shape: GrpcCallShape,
        messages: List<String>,
        methodName: String = shape.methodName,
        deadlineMillis: Long = 30_000L,
    ): GrpcRequestDraft = GrpcRequestDraft(
        targetHost = "127.0.0.1",
        targetPort = server?.port ?: 65_000,
        useTls = false,
        method = GrpcMethodIdentity("lab.ProtocolLab", methodName),
        callShape = shape,
        deadlineMillis = deadlineMillis,
        outboundMessagesJson = messages,
    )

    private fun testService(): ServerServiceDefinition = ServerServiceDefinition.builder("lab.ProtocolLab")
        .addMethod(method("Unary", MethodDescriptor.MethodType.UNARY), ServerCalls.asyncUnaryCall { request, response ->
            response.onNext(request)
            response.onCompleted()
        })
        .addMethod(
            method("ServerStream", MethodDescriptor.MethodType.SERVER_STREAMING),
            ServerCalls.asyncServerStreamingCall { request, response ->
                response.onNext(request)
                response.onNext(request)
                response.onCompleted()
            },
        )
        .addMethod(
            method("ClientStream", MethodDescriptor.MethodType.CLIENT_STREAMING),
            ServerCalls.asyncClientStreamingCall { response ->
                object : StreamObserver<ByteArray> {
                    private var last: ByteArray = byteArrayOf()
                    override fun onNext(value: ByteArray) { last = value }
                    override fun onError(error: Throwable) = Unit
                    override fun onCompleted() {
                        response.onNext(last)
                        response.onCompleted()
                    }
                }
            },
        )
        .addMethod(
            method("Bidi", MethodDescriptor.MethodType.BIDI_STREAMING),
            ServerCalls.asyncBidiStreamingCall { response -> echoEachRequest(response) },
        )
        .addMethod(
            method("Fail", MethodDescriptor.MethodType.UNARY),
            ServerCalls.asyncUnaryCall { _, response ->
                val trailers = Metadata().apply { put(TEST_TRAILER_KEY, "preserved") }
                response.onError(
                    Status.INVALID_ARGUMENT.withDescription("expected failure").asRuntimeException(trailers),
                )
            },
        )
        .addMethod(
            method("Never", MethodDescriptor.MethodType.UNARY),
            ServerCalls.asyncUnaryCall { _, _ -> },
        )
        .build()

    private fun echoEachRequest(response: StreamObserver<ByteArray>): StreamObserver<ByteArray> =
        object : StreamObserver<ByteArray> {
            override fun onNext(value: ByteArray) = response.onNext(value)
            override fun onError(error: Throwable) = Unit
            override fun onCompleted() = response.onCompleted()
        }

    private fun method(name: String, type: MethodDescriptor.MethodType): MethodDescriptor<ByteArray, ByteArray> =
        MethodDescriptor.newBuilder<ByteArray, ByteArray>()
            .setType(type)
            .setFullMethodName(MethodDescriptor.generateFullMethodName("lab.ProtocolLab", name))
            .setRequestMarshaller(TestByteArrayMarshaller)
            .setResponseMarshaller(TestByteArrayMarshaller)
            .build()

    private fun descriptorSet(): DescriptorProtos.FileDescriptorSet {
        val message = DescriptorProtos.DescriptorProto.newBuilder()
            .setName("Echo")
            .addField(
                DescriptorProtos.FieldDescriptorProto.newBuilder()
                    .setName("value")
                    .setNumber(1)
                    .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING),
            )
        val service = DescriptorProtos.ServiceDescriptorProto.newBuilder()
            .setName("ProtocolLab")
            .addMethod(rpc("Unary"))
            .addMethod(rpc("ServerStream", serverStreaming = true))
            .addMethod(rpc("ClientStream", clientStreaming = true))
            .addMethod(rpc("Bidi", clientStreaming = true, serverStreaming = true))
            .addMethod(rpc("Fail"))
            .addMethod(rpc("Never"))
        val file = DescriptorProtos.FileDescriptorProto.newBuilder()
            .setName("protocol_lab.proto")
            .setPackage("lab")
            .setSyntax("proto3")
            .addMessageType(message)
            .addService(service)
        return DescriptorProtos.FileDescriptorSet.newBuilder().addFile(file).build()
    }

    private companion object {
        val TEST_TRAILER_KEY: Metadata.Key<String> = Metadata.Key.of(
            "knet-test-trailer",
            Metadata.ASCII_STRING_MARSHALLER,
        )
    }

    private fun rpc(
        name: String,
        clientStreaming: Boolean = false,
        serverStreaming: Boolean = false,
    ): DescriptorProtos.MethodDescriptorProto = DescriptorProtos.MethodDescriptorProto.newBuilder()
        .setName(name)
        .setInputType(".lab.Echo")
        .setOutputType(".lab.Echo")
        .setClientStreaming(clientStreaming)
        .setServerStreaming(serverStreaming)
        .build()
}

private val GrpcCallShape.methodName: String
    get() = when (this) {
        GrpcCallShape.UNARY -> "Unary"
        GrpcCallShape.SERVER_STREAMING -> "ServerStream"
        GrpcCallShape.CLIENT_STREAMING -> "ClientStream"
        GrpcCallShape.BIDIRECTIONAL_STREAMING -> "Bidi"
    }

private object TestByteArrayMarshaller : MethodDescriptor.Marshaller<ByteArray> {
    override fun stream(value: ByteArray): InputStream = ByteArrayInputStream(value)
    override fun parse(stream: InputStream): ByteArray = stream.use(InputStream::readBytes)
}
