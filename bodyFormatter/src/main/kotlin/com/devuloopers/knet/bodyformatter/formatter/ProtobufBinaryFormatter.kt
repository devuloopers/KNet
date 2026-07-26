package com.devuloopers.knet.bodyformatter.formatter

import com.devuloopers.knet.bodyformatter.model.BodyFormat
import com.google.protobuf.DynamicMessage
import com.google.protobuf.util.JsonFormat
import java.nio.charset.StandardCharsets

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
        // Do not trim bodyText, as binary bytes (like 10 for newline) at the start/end will be lost
        val messageType = headers["x-protobuf-schema"] ?: headers["x-protobuf-message"] ?: ""
        if (messageType.isNotEmpty()) {
            val descriptor = ProtobufDescriptorRegistry.findDescriptor(messageType)
            if (descriptor != null) {
                return try {
                    val bytes = bodyText.toByteArray(StandardCharsets.ISO_8859_1)
                    val message = DynamicMessage.parseFrom(descriptor, bytes)
                    val jsonString = JsonFormat.printer().print(message)
                    BodyFormat.Json(jsonString)
                } catch (e: Exception) {
                    BodyFormat.Protobuf(bodyText)
                }
            }
        }
        
        // Mode 1: Fallback to Raw Binary
        return BodyFormat.Protobuf(bodyText)
    }
}
