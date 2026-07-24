package com.devuloopers.knet.protocol

import com.devuloopers.knet.protocol.grpc.ProtobufDynamicDecoder
import com.devuloopers.knet.protocol.websocket.KNetWebSocketFrameHandler
import com.google.protobuf.DescriptorProtos
import com.google.protobuf.DynamicMessage
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Verification test suite for advanced protocol inspection.
 * Uses Netty's EmbeddedChannel to test WebSockets frames and Protobuf dynamic builder APIs.
 */
class KNetProtocolInspectorTest {

    // ─────────────────────────── WebSockets Interception ───────────────────────────

    @Test
    fun testWebSocketFrameTapping() {
        val recordedList = mutableListOf<WebSocketFrameRecord>()
        val handler = KNetWebSocketFrameHandler(
            roleDirection = FrameDirection.CLIENT_TO_SERVER,
            onFrameRecord = { recordedList.add(it) }
        )
        val channel = EmbeddedChannel(handler)

        // 1. Text Frame (Inbound)
        val textFrame = TextWebSocketFrame("Hello Server")
        assertTrue(channel.writeInbound(textFrame))
        assertEquals(1, recordedList.size)
        val record1 = recordedList.first()
        assertEquals(FrameType.TEXT, record1.type)
        assertEquals(FrameDirection.CLIENT_TO_SERVER, record1.direction)
        assertEquals("Hello Server", record1.payloadText)
        assertEquals(12, record1.length)

        // 2. Binary Frame (Inbound)
        val binaryBytes = byteArrayOf(0x01, 0x02, 0x0A, 0x0F)
        val binaryFrame = BinaryWebSocketFrame(Unpooled.wrappedBuffer(binaryBytes))
        assertTrue(channel.writeInbound(binaryFrame))
        assertEquals(2, recordedList.size)
        val record2 = recordedList.last()
        assertEquals(FrameType.BINARY, record2.type)
        assertEquals("01020A0F", record2.payloadHex)
        assertEquals(4, record2.length)

        // 3. Text Frame (Outbound)
        val responseFrame = TextWebSocketFrame("Hello Client")
        assertTrue(channel.writeOutbound(responseFrame))
        assertEquals(3, recordedList.size)
        val record3 = recordedList.last()
        assertEquals(FrameType.TEXT, record3.type)
        assertEquals(FrameDirection.SERVER_TO_CLIENT, record3.direction)
        assertEquals("Hello Client", record3.payloadText)

        // 4. Ping (Inbound)
        assertTrue(channel.writeInbound(PingWebSocketFrame()))
        assertEquals(FrameType.PING, recordedList.last().type)

        // 5. Pong (Inbound)
        assertTrue(channel.writeInbound(PongWebSocketFrame()))
        assertEquals(FrameType.PONG, recordedList.last().type)

        // 6. Close (Inbound)
        assertTrue(channel.writeInbound(CloseWebSocketFrame(1001, "Going Away")))
        val recordClose = recordedList.last()
        assertEquals(FrameType.CLOSE, recordClose.type)
        assertTrue(recordClose.payloadText?.contains("Close code: 1001") == true)
        assertTrue(recordClose.payloadText?.contains("Going Away") == true)

        channel.finishAndReleaseAll()
    }

    // ─────────────────────────── Dynamic Protobuf Decoding ───────────────────────────

    @Test
    fun testDynamicProtobufDecoding() {
        // Build file descriptor set programmatically to simulate a compiled .desc file
        val fileDescriptorProto = DescriptorProtos.FileDescriptorProto.newBuilder()
            .setName("test.proto")
            .setSyntax("proto3")
            .addMessageType(
                DescriptorProtos.DescriptorProto.newBuilder()
                    .setName("UserInfo")
                    .addField(
                        DescriptorProtos.FieldDescriptorProto.newBuilder()
                            .setName("name")
                            .setNumber(1)
                            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
                    )
                    .addField(
                        DescriptorProtos.FieldDescriptorProto.newBuilder()
                            .setName("age")
                            .setNumber(2)
                            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32)
                    )
            )
            .build()

        val fileDescriptorSet = DescriptorProtos.FileDescriptorSet.newBuilder()
            .addFile(fileDescriptorProto)
            .build()

        val decoder = ProtobufDynamicDecoder()
        decoder.registerSchema(fileDescriptorSet.toByteArray())

        // Create a serialized dynamic message matching the registered schema structure
        val fileDescriptors = com.google.protobuf.Descriptors.FileDescriptor.buildFrom(
            fileDescriptorProto,
            emptyArray()
        )
        val userInfoDescriptor = fileDescriptors.findMessageTypeByName("UserInfo")
        assertNotNull(userInfoDescriptor)

        val sourceMessage = DynamicMessage.newBuilder(userInfoDescriptor)
            .setField(userInfoDescriptor.findFieldByName("name"), "Alice")
            .setField(userInfoDescriptor.findFieldByName("age"), 25)
            .build()

        val serializedBytes = sourceMessage.toByteArray()

        // Decode the bytes using the dynamic decoder
        val jsonOutput = decoder.decodeToJson("UserInfo", serializedBytes)
        assertNotNull(jsonOutput)

        // Standard JSON serialization checks
        assertTrue(jsonOutput.contains("\"name\": \"Alice\""))
        assertTrue(jsonOutput.contains("\"age\": 25"))
    }
}
