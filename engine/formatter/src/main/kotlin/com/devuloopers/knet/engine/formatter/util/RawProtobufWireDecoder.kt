package com.devuloopers.knet.engine.formatter.util

import com.google.protobuf.CodedInputStream
import com.google.protobuf.WireFormat

/**
 * Schema-less Protobuf wire format decoder that inspects raw binary Protobuf byte streams
 * and constructs a structured field tree without requiring any `.proto` schema file.
 *
 * Decodes all 5 Protobuf Wire Types:
 * - WireType 0 (Varint): int32, int64, uint32, uint64, sint32, sint64, bool, enum
 * - WireType 1 (Fixed64): fixed64, sfixed64, double
 * - WireType 2 (Length-Delimited): string, bytes, embedded messages, packed repeated fields
 * - WireType 5 (Fixed32): fixed32, sfixed32, float
 */
object RawProtobufWireDecoder {

    /**
     * Decodes raw binary protobuf bytes into a formatted pseudo-JSON / structured string.
     *
     * @param bytes Raw binary Protobuf byte array.
     * @return Formatted tag tree string, or null if the byte array is invalid Protobuf wire format.
     */
    fun decodeWireFormat(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        return try {
            val input = CodedInputStream.newInstance(bytes)
            val builder = StringBuilder()
            if (decodeFields(input, builder, indentLevel = 0)) {
                val result = builder.toString().trim()
                if (result.isNotEmpty()) result else null
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeFields(input: CodedInputStream, builder: StringBuilder, indentLevel: Int): Boolean {
        var parsedAnyField = false
        val indent = "  ".repeat(indentLevel)

        while (!input.isAtEnd) {
            val tag = input.readTag()
            if (tag == 0) break

            val fieldNumber = WireFormat.getTagFieldNumber(tag)
            val wireType = WireFormat.getTagWireType(tag)

            if (fieldNumber <= 0 || fieldNumber > 536870911) {
                return false // Invalid field number per Protobuf spec
            }

            parsedAnyField = true

            when (wireType) {
                WireFormat.WIRETYPE_VARINT -> {
                    val value = input.readInt64()
                    builder.append(indent).append("field_").append(fieldNumber).append(": ").append(value).append("\n")
                }
                WireFormat.WIRETYPE_FIXED64 -> {
                    val value = input.readFixed64()
                    val doubleVal = Double.fromBits(value)
                    val displayVal = if (!doubleVal.isNaN() && !doubleVal.isInfinite() && doubleVal != 0.0 && Math.abs(doubleVal) < 1e12 && Math.abs(doubleVal) > 1e-12) {
                        "$value (double: $doubleVal)"
                    } else {
                        "$value"
                    }
                    builder.append(indent).append("field_").append(fieldNumber).append(": ").append(displayVal).append("\n")
                }
                WireFormat.WIRETYPE_LENGTH_DELIMITED -> {
                    val rawBytes = input.readBytes().toByteArray()
                    if (rawBytes.isEmpty()) {
                        builder.append(indent).append("field_").append(fieldNumber).append(": \"\"\n")
                    } else {
                        val nestedBuilder = StringBuilder()
                        val isNested = try {
                            val nestedInput = CodedInputStream.newInstance(rawBytes)
                            decodeFields(nestedInput, nestedBuilder, indentLevel + 1)
                        } catch (_: Exception) {
                            false
                        }

                        if (isNested && nestedBuilder.isNotBlank()) {
                            builder.append(indent).append("field_").append(fieldNumber).append(" {\n")
                            builder.append(nestedBuilder)
                            builder.append(indent).append("}\n")
                        } else {
                            val text = String(rawBytes, Charsets.UTF_8)
                            if (isPrintableUtf8(rawBytes, text)) {
                                val escaped = text.replace("\"", "\\\"").replace("\n", "\\n")
                                builder.append(indent).append("field_").append(fieldNumber).append(": \"").append(escaped).append("\"\n")
                            } else {
                                val hex = rawBytes.take(32).joinToString(" ") { "%02X".format(it) }
                                val suffix = if (rawBytes.size > 32) " ... (${rawBytes.size} bytes)" else ""
                                builder.append(indent).append("field_").append(fieldNumber).append(": [bytes: ").append(hex).append(suffix).append("]\n")
                            }
                        }
                    }
                }
                WireFormat.WIRETYPE_FIXED32 -> {
                    val value = input.readFixed32()
                    val floatVal = Float.fromBits(value)
                    val displayVal = if (!floatVal.isNaN() && !floatVal.isInfinite() && floatVal != 0.0f && Math.abs(floatVal) < 1e6f && Math.abs(floatVal) > 1e-6f) {
                        "$value (float: $floatVal)"
                    } else {
                        "$value"
                    }
                    builder.append(indent).append("field_").append(fieldNumber).append(": ").append(displayVal).append("\n")
                }
                WireFormat.WIRETYPE_START_GROUP,
                WireFormat.WIRETYPE_END_GROUP -> {
                    return false
                }
                else -> return false
            }
        }
        return parsedAnyField
    }

    private fun isPrintableUtf8(bytes: ByteArray, text: String): Boolean {
        if (text.isEmpty()) return false
        var printableCount = 0
        for (char in text) {
            if (char.isLetterOrDigit() || char.isWhitespace() || char in "!@#$%^&*()_+-=[]{}|;:'\",.<>/?\\`~") {
                printableCount++
            }
        }
        return (printableCount.toDouble() / text.length) >= 0.85
    }
}
