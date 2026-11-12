package com.devuloopers.knet.engine.grpc

import com.devuloopers.knet.domain.request.descriptor.RequestDescriptorInput
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.HeaderName
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.google.protobuf.DescriptorProtos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GrpcDescriptorRegistryTest {
    @Test
    fun `descriptor set resolves method cardinality and decodes protobuf payload`() {
        val registry = GrpcDescriptorRegistry()
        val imported = registry.importDescriptorSet(GrpcDescriptorSourceId("echo"), echoDescriptorSet())
            .getOrThrow()

        assertEquals(1, imported.serviceCount)
        assertEquals(1, imported.methodCount)
        val identity = GrpcMethodIdentity("test.echo.EchoService", "UnaryEcho")
        val method = requireNotNull(registry.resolve(identity))
        assertEquals("test.echo.EchoRequest", method.requestType)
        assertEquals(false, method.clientStreaming)
        assertEquals(false, method.serverStreaming)

        val decoded = assertIs<GrpcPayloadDecodeResult.DecodedJson>(
            registry.decode(
                identity = identity,
                direction = GrpcPayloadDirection.REQUEST,
                payload = byteArrayOf(0x0a, 0x02, 'h'.code.toByte(), 'i'.code.toByte()),
            ),
        )
        assertTrue("\"text\": \"hi\"" in decoded.json)
    }

    @Test
    fun `corrupt import cannot replace previously valid immutable state`() {
        val registry = GrpcDescriptorRegistry()
        registry.importDescriptorSet(GrpcDescriptorSourceId("echo"), echoDescriptorSet()).getOrThrow()

        assertTrue(registry.importDescriptorSet(GrpcDescriptorSourceId("bad"), byteArrayOf(1, 2, 3)).isFailure)
        assertEquals(1, registry.methods().size)
        assertNull(registry.resolve(GrpcMethodIdentity("missing.Service", "Call")))
    }

    @Test
    fun `request descriptor gives native grpc a unified RPC identity`() {
        val descriptor = GrpcRequestDescriptorStrategy().describe(
            RequestDescriptorInput(
                transportMethod = HttpMethod.POST,
                absoluteUrl = "https://localhost:8443/test.echo.EchoService/UnaryEcho",
                headers = listOf(HeaderField(HeaderName("content-type"), "application/grpc+proto")),
            ),
        )

        requireNotNull(descriptor)
        assertEquals(RequestKindId.GRPC, descriptor.kind)
        assertEquals("RPC", descriptor.badgeLabel)
        assertEquals("test.echo.EchoService/UnaryEcho", descriptor.suggestedName)
    }

    @Test
    fun `message presentation exposes captured descriptor identity and refuses partial previews`() {
        val registry = GrpcDescriptorRegistry()
        registry.importDescriptorSet(GrpcDescriptorSourceId("echo"), echoDescriptorSet()).getOrThrow()
        val exchange = com.devuloopers.knet.traffic.model.HttpExchangeSnapshot(
            com.devuloopers.knet.traffic.id.ExchangeId("grpc"),
            request = com.devuloopers.knet.traffic.model.HttpRequestSnapshot(com.devuloopers.knet.traffic.model.http.RequestHead(
                HttpMethod.POST, com.devuloopers.knet.traffic.model.http.RequestTarget.Absolute(
                    com.devuloopers.knet.traffic.model.http.HttpScheme.fromToken("https"),
                    com.devuloopers.knet.traffic.model.http.Authority("example.test"), "/test.echo.EchoService/UnaryEcho"),
                com.devuloopers.knet.traffic.model.http.ApplicationProtocol.fromToken("HTTP/2"), emptyList())),
            state = com.devuloopers.knet.traffic.model.ExchangeState.COMPLETED, startedAtEpochMillis = 1)
        val message = com.devuloopers.knet.traffic.model.message.ProtocolMessageSnapshot(
            com.devuloopers.knet.traffic.id.ProtocolMessageId("message"), com.devuloopers.knet.traffic.id.ConnectionId("connection"),
            exchange.id, null, com.devuloopers.knet.traffic.model.message.MessageProtocolId.GRPC,
            com.devuloopers.knet.traffic.model.message.ProtocolMessageKind.DATA,
            com.devuloopers.knet.traffic.model.TrafficDirection.CLIENT_TO_SERVER, 1, 1, null, 4, false, null,
            com.devuloopers.knet.traffic.model.body.MessageBodyRef.Empty,
            com.devuloopers.knet.traffic.model.message.ProtocolMessageState.COMPLETE)
        val input = com.devuloopers.knet.application.contract.traffic.ProtocolMessagePayloadInput(exchange, message,
            byteArrayOf(0x0a, 0x02, 'h'.code.toByte(), 'i'.code.toByte()))
        val decoder = GrpcProtocolMessageDecoder(registry)
        assertEquals("test.echo.EchoRequest", decoder.decode(input)!!.fields.first { it.label == "Message type" }.value)
        assertNull(decoder.decode(input.copy(truncated = true)))
        assertNull(decoder.decode(input.copy(message = message.copy(exchangeId = com.devuloopers.knet.traffic.id.ExchangeId("other")))))
    }

    private fun echoDescriptorSet(): ByteArray {
        val request = DescriptorProtos.DescriptorProto.newBuilder()
            .setName("EchoRequest")
            .addField(
                DescriptorProtos.FieldDescriptorProto.newBuilder()
                    .setName("text")
                    .setNumber(1)
                    .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING),
            )
        val response = DescriptorProtos.DescriptorProto.newBuilder()
            .setName("EchoResponse")
            .addField(
                DescriptorProtos.FieldDescriptorProto.newBuilder()
                    .setName("text")
                    .setNumber(1)
                    .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING),
            )
        val service = DescriptorProtos.ServiceDescriptorProto.newBuilder()
            .setName("EchoService")
            .addMethod(
                DescriptorProtos.MethodDescriptorProto.newBuilder()
                    .setName("UnaryEcho")
                    .setInputType(".test.echo.EchoRequest")
                    .setOutputType(".test.echo.EchoResponse"),
            )
        val file = DescriptorProtos.FileDescriptorProto.newBuilder()
            .setName("echo.proto")
            .setPackage("test.echo")
            .setSyntax("proto3")
            .addMessageType(request)
            .addMessageType(response)
            .addService(service)
            .build()
        return DescriptorProtos.FileDescriptorSet.newBuilder().addFile(file).build().toByteArray()
    }
}
