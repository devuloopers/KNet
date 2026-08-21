package com.devuloopers.knet.core.http.client

import com.devuloopers.knet.domain.clientNetwork.model.OutboundRequestBody
import io.ktor.http.encodeURLParameter

/** Single JVM body encoder shared by exact HTTP/1.0 and HTTP/2 transports. */
internal data class EncodedTransportBody(
    val bytes: ByteArray,
    val contentType: String?,
)

internal fun OutboundRequestBody.encodeForTransport(): EncodedTransportBody = when (this) {
    OutboundRequestBody.None -> EncodedTransportBody(byteArrayOf(), null)
    is OutboundRequestBody.Json -> EncodedTransportBody(content.encodeToByteArray(), "application/json")
    is OutboundRequestBody.Xml -> EncodedTransportBody(content.encodeToByteArray(), "application/xml")
    is OutboundRequestBody.Text -> EncodedTransportBody(content.encodeToByteArray(), mediaType)
    is OutboundRequestBody.GraphQl -> {
        val trimmed = content.trim()
        val json = when {
            trimmed.isBlank() -> "{}"
            trimmed.startsWith('{') -> trimmed
            else -> "{\"query\": \"${trimmed.escapeJsonString()}\"}"
        }
        EncodedTransportBody(json.encodeToByteArray(), "application/json")
    }
    is OutboundRequestBody.FormUrlEncoded -> EncodedTransportBody(
        fields.joinToString("&") { field ->
            "${field.name.formEncode()}=${field.value.formEncode()}"
        }.encodeToByteArray(),
        "application/x-www-form-urlencoded",
    )
    is OutboundRequestBody.Multipart -> {
        val boundary = "KNet-${System.nanoTime().toString(16)}"
        val content = buildString {
            fields.forEach { field ->
                append("--").append(boundary).append("\r\n")
                append("Content-Disposition: form-data; name=\"")
                    .append(field.name.sanitizeQuotedHeaderValue()).append("\"\r\n\r\n")
                append(field.value).append("\r\n")
            }
            append("--").append(boundary).append("--\r\n")
        }
        EncodedTransportBody(content.encodeToByteArray(), "multipart/form-data; boundary=$boundary")
    }
}

private fun String.formEncode(): String = encodeURLParameter(spaceToPlus = false)

private fun String.escapeJsonString(): String = buildString {
    this@escapeJsonString.forEach { character ->
        append(
            when (character) {
                '\\' -> "\\\\"
                '"' -> "\\\""
                '\n' -> "\\n"
                '\r' -> "\\r"
                '\t' -> "\\t"
                else -> character
            },
        )
    }
}

private fun String.sanitizeQuotedHeaderValue(): String = replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\r", "")
    .replace("\n", "")
