package com.devuloopers.knet.bodyformatter.formatter

import com.devuloopers.knet.bodyformatter.model.BodyFormat

/**
 * Strategy formatter for Protobuf and binary payload descriptors.
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
        return BodyFormat.Protobuf(bodyText.trim())
    }
}
