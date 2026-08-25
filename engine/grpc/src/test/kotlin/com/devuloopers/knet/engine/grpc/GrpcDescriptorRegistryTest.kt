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
