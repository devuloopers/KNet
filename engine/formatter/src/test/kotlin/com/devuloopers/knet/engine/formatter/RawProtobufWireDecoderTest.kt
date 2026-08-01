package com.devuloopers.knet.engine.formatter

import com.devuloopers.knet.engine.formatter.formatters.ProtobufBinaryFormatter
import com.devuloopers.knet.engine.formatter.model.BodyFormat
import com.devuloopers.knet.engine.formatter.util.RawProtobufWireDecoder
import com.google.protobuf.CodedOutputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RawProtobufWireDecoderTest {

    @Test
    fun testDecodeRawProtobufWireFields() {
        val baos = ByteArrayOutputStream()
        val cos = CodedOutputStream.newInstance(baos)

        cos.writeInt64(1, 42L)
        cos.writeString(2, "ChromeSyncSession")
        cos.flush()

        val rawBytes = baos.toByteArray()
        val decoded = RawProtobufWireDecoder.decodeWireFormat(rawBytes)

        assertNotNull(decoded)
        assertTrue(decoded.contains("field_1: 42"))
        assertTrue(decoded.contains("field_2: \"ChromeSyncSession\""))
    }

    @Test
    fun testProtobufBinaryFormatterFallbackToWireDecoder() {
        val baos = ByteArrayOutputStream()
        val cos = CodedOutputStream.newInstance(baos)

        cos.writeInt32(1, 200)
        cos.writeString(2, "SUCCESS")
        cos.flush()

        val rawBytes = baos.toByteArray()
        val rawBodyText = String(rawBytes, StandardCharsets.ISO_8859_1)

        val headers = mapOf("content-type" to "application/vnd.google.octet-stream-compressible")
        val formatter = ProtobufBinaryFormatter()

        val result = formatter.format(headers, rawBodyText)
        assertTrue(result is BodyFormat.Protobuf)
        assertTrue(result.descriptor.contains("field_1: 200"))
        assertTrue(result.descriptor.contains("field_2: \"SUCCESS\""))
    }
}
