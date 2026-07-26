package com.devuloopers.knet.bodyformatter.formatter

import com.devuloopers.knet.bodyformatter.model.BodyFormat
import com.devuloopers.knet.bodyformatter.model.GrpcWebFrame
import com.google.protobuf.DynamicMessage
import com.google.protobuf.util.JsonFormat
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Strategy formatter for gRPC-Web binary streams.
 * Parses length-prefixed blocks (Data & Trailer frames) and dynamically decodes their contents.
 */
class GrpcWebBodyFormatter(
    private val jsonFormatter: JsonBodyFormatter = JsonBodyFormatter()
) : BodyFormatter {
    override val priority: Int = 95

    override fun matches(headers: Map<String, String>, bodyText: String): Boolean {
        val contentType = headers.entries.find { it.key.equals("content-type", ignoreCase = true) }?.value ?: ""
        val mime = contentType.lowercase()
        return mime.contains("grpc-web") || mime.contains("grpc-web-text")
    }

    override fun format(headers: Map<String, String>, bodyText: String): BodyFormat {
        val contentType = headers.entries.find { it.key.equals("content-type", ignoreCase = true) }?.value ?: ""
        val isBase64Text = contentType.lowercase().contains("grpc-web-text")

        val rawBytes = try {
            val rawStringBytes = bodyText.toByteArray(StandardCharsets.ISO_8859_1)
            if (isBase64Text) {
                // Decode base64 stream
                Base64.getDecoder().decode(bodyText.trim().toByteArray(StandardCharsets.US_ASCII))
            } else {
                rawStringBytes
            }
        } catch (_: Exception) {
            return BodyFormat.RawText(bodyText)
        }

        val frames = mutableListOf<GrpcWebFrame>()
        var offset = 0

        while (offset + 5 <= rawBytes.size) {
            val flag = rawBytes[offset].toInt() and 0xFF
            val length = ByteBuffer.wrap(rawBytes, offset + 1, 4).int
            offset += 5

            if (offset + length > rawBytes.size) {
                // Truncated/corrupted stream chunk
                break
            }

            val payloadBytes = rawBytes.copyOfRange(offset, offset + length)
            offset += length

            val isTrailer = (flag and 0x80) != 0
            val payloadHex = payloadBytes.joinToString("") { "%02X".format(it) }

            val decodedText = if (isTrailer) {
                // Trailer frame contains text metadata/headers
                String(payloadBytes, StandardCharsets.UTF_8).trim()
            } else {
                // Data frame contains JSON or Protobuf payload
                val mimeLower = contentType.lowercase()
                when {
                    mimeLower.contains("json") -> {
                        val rawJson = String(payloadBytes, StandardCharsets.UTF_8)
                        jsonFormatter.prettyPrintJson(rawJson)
                    }
                    else -> {
                        // gRPC-Web usually specifies protobuf. Attempt to parse schema:
                        val messageType = headers["x-protobuf-schema"] ?: headers["x-protobuf-message"] ?: ""
                        val descriptor = if (messageType.isNotEmpty()) {
                            ProtobufDescriptorRegistry.findDescriptor(messageType)
                        } else null

                        if (descriptor != null) {
                            try {
                                val message = DynamicMessage.parseFrom(descriptor, payloadBytes)
                                JsonFormat.printer().print(message)
                            } catch (_: Exception) {
                                formatHexFallback(payloadBytes)
                            }
                        } else {
                            formatHexFallback(payloadBytes)
                        }
                    }
                }
            }

            frames.add(GrpcWebFrame(isTrailer, payloadHex, decodedText))
        }

        if (frames.isEmpty() && bodyText.isNotEmpty()) {
            return BodyFormat.RawText(bodyText)
        }

        return BodyFormat.GrpcWeb(frames)
    }

    private fun formatHexFallback(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append("=== Raw Hex (Schema-less Fallback) ===\n")
        var i = 0
        while (i < bytes.size) {
            val hex = bytes.copyOfRange(i, minOf(i + 16, bytes.size)).joinToString(" ") { "%02X".format(it) }
            val ascii = bytes.copyOfRange(i, minOf(i + 16, bytes.size)).map {
                val c = it.toInt().toChar()
                if (c in ' '..'~') c else '.'
            }.joinToString("")
            sb.append("%04X  %-48s  |%s|\n".format(i, hex, ascii))
            i += 16
        }
        return sb.toString().trim()
    }
}
