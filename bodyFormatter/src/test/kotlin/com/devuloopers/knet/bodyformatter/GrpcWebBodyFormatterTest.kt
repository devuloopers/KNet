package com.devuloopers.knet.bodyformatter

import com.devuloopers.knet.bodyformatter.formatter.GrpcWebBodyFormatter
import com.devuloopers.knet.bodyformatter.formatter.ProtobufDescriptorRegistry
import com.devuloopers.knet.bodyformatter.model.BodyFormat
import com.google.protobuf.DescriptorProtos
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GrpcWebBodyFormatterTest {
    private val formatter = GrpcWebBodyFormatter()

    @AfterTest
    fun cleanup() {
        ProtobufDescriptorRegistry.clear()
    }

    @Test
    fun testMatchesGrpcWebContentTypes() {
        assertTrue(formatter.matches(mapOf("Content-Type" to "application/grpc-web"), ""))
        assertTrue(formatter.matches(mapOf("content-type" to "application/grpc-web+proto"), ""))
        assertTrue(formatter.matches(mapOf("Content-Type" to "application/grpc-web-text"), ""))
        assertTrue(formatter.matches(mapOf("content-type" to "application/grpc-web-text+json"), ""))
    }

    @Test
    fun testFormatBinaryGrpcWebFrames() {
        // Construct raw gRPC-web frames:
        // Frame 1: Data frame (flag 0x00), length 4, payload = "KNet"
        // Frame 2: Trailer frame (flag 0x80), length 13, payload = "grpc-status:0"
        val out = ByteArrayOutputStream()
        
        // Frame 1 header
        out.write(0) // flag
        out.write(ByteBuffer.allocate(4).putInt(4).array()) // length
        out.write("KNet".toByteArray(StandardCharsets.UTF_8)) // payload

        // Frame 2 header
        out.write(0x80) // flag
        out.write(ByteBuffer.allocate(4).putInt(13).array()) // length
        out.write("grpc-status:0".toByteArray(StandardCharsets.UTF_8)) // payload

        val bodyText = String(out.toByteArray(), Charsets.ISO_8859_1)

        val result = formatter.format(mapOf("content-type" to "application/grpc-web"), bodyText)
        assertTrue(result is BodyFormat.GrpcWeb)
        assertEquals(2, result.frames.size)

        val dataFrame = result.frames[0]
        assertEquals(false, dataFrame.isTrailer)
        assertTrue(dataFrame.decodedJsonOrText.contains("KNet") || dataFrame.decodedJsonOrText.contains("4B 4E 65 74"))

        val trailerFrame = result.frames[1]
        assertEquals(true, trailerFrame.isTrailer)
        assertEquals("grpc-status:0", trailerFrame.decodedJsonOrText)
    }

    @Test
    fun testFormatBase64TextGrpcWebFrames() {
        // Construct raw gRPC-web frames same as above but base64 encode them
        val out = ByteArrayOutputStream()
        
        // Frame 1 header
        out.write(0) // flag
        out.write(ByteBuffer.allocate(4).putInt(4).array()) // length
        out.write("KNet".toByteArray(StandardCharsets.UTF_8)) // payload

        // Frame 2 header
        out.write(0x80) // flag
        out.write(ByteBuffer.allocate(4).putInt(13).array()) // length
        out.write("grpc-status:0".toByteArray(StandardCharsets.UTF_8)) // payload

        val base64EncodedText = Base64.getEncoder().encodeToString(out.toByteArray())

        val result = formatter.format(mapOf("content-type" to "application/grpc-web-text"), base64EncodedText)
        assertTrue(result is BodyFormat.GrpcWeb)
        assertEquals(2, result.frames.size)

        val dataFrame = result.frames[0]
        assertEquals(false, dataFrame.isTrailer)
        assertTrue(dataFrame.decodedJsonOrText.contains("KNet") || dataFrame.decodedJsonOrText.contains("4B 4E 65 74"))

        val trailerFrame = result.frames[1]
        assertEquals(true, trailerFrame.isTrailer)
        assertEquals("grpc-status:0", trailerFrame.decodedJsonOrText)
    }

    @Test
    fun testFormatSchemaDecodedGrpcWebFrames() {
        // Build a dynamic FileDescriptorProto representing a test message schema:
        // message TestMessage { string name = 1; }
        val fileProto = DescriptorProtos.FileDescriptorProto.newBuilder()
            .setName("test.proto")
            .setPackage("com.example")
            .addMessageType(
                DescriptorProtos.DescriptorProto.newBuilder()
                    .setName("TestMessage")
                    .addField(
                        DescriptorProtos.FieldDescriptorProto.newBuilder()
                            .setName("name")
                            .setNumber(1)
                            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
                    )
            )
            .build()

        val setProto = DescriptorProtos.FileDescriptorSet.newBuilder()
            .addFile(fileProto)
            .build()

        val descOut = ByteArrayOutputStream()
        setProto.writeTo(descOut)
        val descInputStream = ByteArrayInputStream(descOut.toByteArray())

        // Register the schema
        ProtobufDescriptorRegistry.registerSchema(descInputStream)

        // Construct raw gRPC-web frames:
        // Frame 1: Data frame (flag 0x00), length 6, payload = Protobuf(10, 4, 75, 78, 101, 116)
        val out = ByteArrayOutputStream()
        out.write(0) // flag
        out.write(ByteBuffer.allocate(4).putInt(6).array()) // length
        out.write(byteArrayOf(10, 4, 75, 78, 101, 116)) // payload

        val bodyText = String(out.toByteArray(), Charsets.ISO_8859_1)

        val headers = mapOf(
            "content-type" to "application/grpc-web+proto",
            "x-protobuf-schema" to "com.example.TestMessage"
        )
        val result = formatter.format(headers, bodyText)
        assertTrue(result is BodyFormat.GrpcWeb)
        assertEquals(1, result.frames.size)

        val dataFrame = result.frames[0]
        assertEquals(false, dataFrame.isTrailer)
        // Schema is matched and decodes to JSON representation
        assertTrue(dataFrame.decodedJsonOrText.contains("KNet"))
    }
}
