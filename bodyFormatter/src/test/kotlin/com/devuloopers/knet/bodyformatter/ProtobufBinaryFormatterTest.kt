package com.devuloopers.knet.bodyformatter

import com.devuloopers.knet.bodyformatter.formatter.ProtobufBinaryFormatter
import com.devuloopers.knet.bodyformatter.formatter.ProtobufDescriptorRegistry
import com.devuloopers.knet.bodyformatter.model.BodyFormat
import com.google.protobuf.DescriptorProtos
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProtobufBinaryFormatterTest {
    private val formatter = ProtobufBinaryFormatter()

    @AfterTest
    fun cleanup() {
        ProtobufDescriptorRegistry.clear()
    }

    @Test
    fun testMode1FallbackToRawTextForUnknownProtobuf() {
        val rawData = "[Binary payload — 1.5 KB · application/x-protobuf]"
        val formatResult = formatter.format(emptyMap(), rawData)

        assertTrue(formatResult is BodyFormat.Protobuf)
        assertEquals(rawData, formatResult.descriptor)
    }

    @Test
    fun testMode2DynamicSchemaDecoding() {
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

        // Verify the descriptor is registered
        val descriptor = ProtobufDescriptorRegistry.findDescriptor("com.example.TestMessage")
        assertTrue(descriptor != null)

        // Construct a raw binary message bytes manually:
        // field tag = 1 (type = String / length-delimited = wire type 2) -> (1 << 3) | 2 = 10
        // length = 4
        // value = "KNet"
        val rawMessageBytes = byteArrayOf(10, 4, 75, 78, 101, 116) // "KNet" in ASCII
        val bodyText = String(rawMessageBytes, Charsets.ISO_8859_1)

        val headers = mapOf("x-protobuf-schema" to "com.example.TestMessage")
        val formatResult = formatter.format(headers, bodyText)

        // Mode 2: Resolved Schema formats it as BodyFormat.Json
        assertTrue(formatResult is BodyFormat.Json)
        assertTrue(formatResult.formattedText.contains("KNet"))
    }
}
