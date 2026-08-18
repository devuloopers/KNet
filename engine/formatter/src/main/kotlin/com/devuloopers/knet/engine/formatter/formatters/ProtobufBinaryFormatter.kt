package com.devuloopers.knet.engine.formatter.formatters

import com.devuloopers.knet.engine.formatter.BodyFormatter
import com.devuloopers.knet.engine.formatter.model.BodyFormat
import com.devuloopers.knet.engine.formatter.util.RawProtobufWireDecoder
import com.google.protobuf.DynamicMessage
import com.google.protobuf.util.JsonFormat

/**
 * Strategy formatter for Protobuf and binary payload descriptors.
 * Supports Mode 2 (Schema Available decoding via DynamicMessage) and Mode 1 (Unknown Raw Bytes).
 */
class ProtobufBinaryFormatter : BodyFormatter {
    override val priority: Int = 100

    override fun matches(headers: Map<String, String>, bodyText: String): Boolean {
        val contentType = headers.entries.find { it.key.equals("content-type", ignoreCase = true) }?.value ?: ""
        val mime = contentType.substringBefore(";").trim().lowercase()
        val trimmed = bodyText.trim()

        return trimmed.startsWith("[Binary payload") || trimmed.startsWith("[Binary") || mime.contains("proto")
    }

    override fun format(headers: Map<String, String>, bodyText: String): BodyFormat {
        val bytes = bodyText.toByteArray(Charsets.ISO_8859_1)

        val messageType = headers["x-protobuf-schema"] ?: headers["x-protobuf-message"] ?: ""
        if (messageType.isNotEmpty()) {
            val descriptor = ProtobufDescriptorRegistry.findDescriptor(messageType)
            if (descriptor != null) {
                try {
                    val message = DynamicMessage.parseFrom(descriptor, bytes)
                    val jsonString = JsonFormat.printer().print(message)
                    return BodyFormat.Json(jsonString)
                } catch (_: Exception) {
                    // Fallthrough to Stage 2
                }
            }
        }

        val decodedWire = RawProtobufWireDecoder.decodeWireFormat(bytes)
        if (decodedWire != null) {
            return BodyFormat.Protobuf(decodedWire)
        }

        return BodyFormat.Protobuf(bodyText)
    }
}
