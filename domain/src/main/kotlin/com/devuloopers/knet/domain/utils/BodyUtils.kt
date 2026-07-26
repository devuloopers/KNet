package com.devuloopers.knet.domain.utils

import com.devuloopers.knet.bodyformatter.formatter.BodyFormatterRegistry
import com.devuloopers.knet.bodyformatter.formatter.JsonBodyFormatter
import com.devuloopers.knet.bodyformatter.model.BodyFormat
import com.github.luben.zstd.ZstdInputStream
import org.brotli.dec.BrotliInputStream
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.nio.charset.IllegalCharsetNameException

/**
 * Utility functions for decoding and formatting HTTP request/response body payloads.
 *
 * Handles the full decode pipeline in order:
 *  1. Binary / non-text content detection (protobuf, octet-stream, images, etc.)
 *  2. Content-Encoding decompression: gzip, deflate, br (Brotli)
 *  3. Charset extraction from Content-Type for correct string conversion
 *  4. JSON pretty-printing for readable output
 */

/** Set of MIME type prefixes that are known to be non-text binary formats. */
private val BINARY_CONTENT_TYPES = setOf(
    "application/x-protobuf",
    "application/protobuf",
    "application/vnd.google.protobuf",
    "application/x-protobuffer",
    "application/octet-stream",
    "application/x-www-form-urlencoded-binary",
    "application/grpc",
    "application/grpc+proto",
    "application/binary",
    "application/cbor",
    "application/msgpack",
    "application/x-msgpack",
    "application/x-gzip",
    "application/gzip",
    "image/",
    "audio/",
    "video/",
    "font/",
    "application/wasm",
    "application/zip",
    "application/pdf"
)

/**
 * Checks if a byte array contains binary control characters or null bytes.
 */
private fun ByteArray.isBinaryContent(): Boolean {
    if (isEmpty()) return false
    val sampleSize = minOf(size, 512)
    var nullCount = 0
    var controlCount = 0
    for (i in 0 until sampleSize) {
        val b = this[i].toInt() and 0xFF
        if (b == 0) nullCount++
        else if (b < 9 || (b in 14..31)) controlCount++
    }
    return nullCount > 0 || (controlCount.toDouble() / sampleSize) > 0.10
}

/**
 * Decompresses compressed byte payloads (gzip, deflate, br, zstd).
 */
fun decompressBody(body: ByteArray, contentEncoding: String?): ByteArray {
    if (body.isEmpty() || contentEncoding.isNullOrEmpty()) return body
    val encoding = contentEncoding.trim().lowercase()

    return try {
        when {
            encoding.contains("gzip") -> java.util.zip.GZIPInputStream(ByteArrayInputStream(body)).readAllBytesCompat()
            encoding.contains("deflate") -> java.util.zip.InflaterInputStream(ByteArrayInputStream(body)).readAllBytesCompat()
            encoding.contains("br") -> BrotliInputStream(ByteArrayInputStream(body)).readAllBytesCompat()
            encoding.contains("zstd") -> ZstdInputStream(ByteArrayInputStream(body)).readAllBytesCompat()
            else -> body
        }
    } catch (_: Exception) {
        body
    }
}

private fun java.io.InputStream.readAllBytesCompat(): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(4096)
    var len: Int
    while (this.read(buf).also { len = it } != -1) {
        out.write(buf, 0, len)
    }
    return out.toByteArray()
}

/**
 * Parses `Content-Type` header value to extract charset encoding name.
 */
fun parseCharsetFromContentType(contentTypeHeader: String?): Charset {
    if (contentTypeHeader.isNullOrEmpty()) return Charsets.UTF_8

    val parts = contentTypeHeader.split(";")
    for (part in parts) {
        val trimmed = part.trim()
        if (trimmed.lowercase().startsWith("charset=")) {
            val name = trimmed.substringAfter("=").trim().trim('"', '\'')
            if (name.isNotEmpty()) {
                try {
                    return Charset.forName(name)
                } catch (_: IllegalCharsetNameException) {
                } catch (_: java.nio.charset.UnsupportedCharsetException) {
                }
            }
        }
    }
    return Charsets.UTF_8
}

/**
 * Core entry point to decode binary byte payloads into formatted text strings.
 */
fun decodeBodyToText(bodyBytes: ByteArray?, headers: List<Pair<String, String>>): String {
    if (bodyBytes == null || bodyBytes.isEmpty()) return ""

    val contentTypeHeader = headers.find { it.first.equals("Content-Type", ignoreCase = true) }?.second ?: ""
    val mimeType = contentTypeHeader.substringBefore(";").trim().lowercase()

    val encodingHeader = headers.find { it.first.equals("Content-Encoding", ignoreCase = true) }?.second

    val decompressed = decompressBody(bodyBytes, encodingHeader)

    val sizeKb = "%.1f KB".format(decompressed.size / 1024.0)
    val isBinaryMime = BINARY_CONTENT_TYPES.any { mimeType.startsWith(it) }
    val isBinaryBytes = decompressed.isBinaryContent()

    if (isBinaryMime || isBinaryBytes) {
        val label = mimeType.ifEmpty { "binary" }
        return "[Binary payload — $sizeKb · $label]"
    }

    val charset = parseCharsetFromContentType(contentTypeHeader)
    return String(decompressed, charset)
}

/**
 * Strongly typed representation of HTTP payload body content categories.
 */
enum class BodyContentType(val badgeLabel: String) {
    JSON("JSON"),
    JSON_STREAM("JSON Stream"),
    FORM_DATA("Form Data"),
    SSE_STREAM("SSE Stream"),
    GRPC_STREAM("gRPC Stream"),
    IMAGE("Image"),
    PROTOBUF("Protobuf"),
    @Suppress("unused") HTML("HTML"),
    @Suppress("unused") XML("XML"),
    @Suppress("unused") JS("JS"),
    @Suppress("unused") CSS("CSS"),
    @Suppress("unused") PDF("PDF"),
    PLAIN_TEXT("PLAIN")
}

/**
 * Detects the strongly-typed [BodyContentType] of an HTTP payload using [BodyFormatterRegistry].
 */
fun detectContentType(headers: Map<String, String>, bodyText: String): BodyContentType {
    val format = BodyFormatterRegistry.resolveFormat(headers, bodyText)
    val typeHeader = headers.entries.find { it.key.equals("content-type", ignoreCase = true) }?.value ?: ""
    val mime = typeHeader.substringBefore(";").trim().lowercase()

    return when (format) {
        is BodyFormat.Json -> BodyContentType.JSON
        is BodyFormat.JsonStream -> {
            if (mime.contains("grpc") && !bodyText.contains("{")) BodyContentType.GRPC_STREAM else BodyContentType.JSON_STREAM
        }
        is BodyFormat.FormData -> BodyContentType.FORM_DATA
        is BodyFormat.SseStream -> BodyContentType.SSE_STREAM
        is BodyFormat.Protobuf -> BodyContentType.PROTOBUF
        is BodyFormat.Image -> BodyContentType.IMAGE
        is BodyFormat.Html -> BodyContentType.HTML
        is BodyFormat.Xml -> BodyContentType.XML
        is BodyFormat.Cbor -> BodyContentType.JSON
        is BodyFormat.Js -> BodyContentType.JS
        is BodyFormat.Css -> BodyContentType.CSS
        is BodyFormat.GrpcWeb -> BodyContentType.PLAIN_TEXT
        is BodyFormat.RawText -> {
            if (mime.contains("protobuf") || mime.contains("proto")) BodyContentType.PROTOBUF
            else if (mime.startsWith("image/")) BodyContentType.IMAGE
            else BodyContentType.PLAIN_TEXT
        }
    }
}

/**
 * Resolves a human-readable display label for UI badge pills using [BodyFormatterRegistry].
 */
fun detectContentTypeLabel(headers: Map<String, String>, bodyText: String): String {
    val typeHeader = headers.entries.find { it.key.equals("content-type", ignoreCase = true) }?.value ?: ""
    val mime = typeHeader.substringBefore(";").trim().lowercase()
    if (mime.startsWith("image/")) {
        val sub = mime.substringAfter("/").uppercase()
        return "$sub Image"
    }
    if (mime.contains("grpc") && !bodyText.contains("{")) {
        return "gRPC Stream"
    }
    val format = BodyFormatterRegistry.resolveFormat(headers, bodyText)
    return format.badgeLabel
}

/**
 * Formats body payload strings using [BodyFormatterRegistry] for human-readable display.
 */
fun prettyPrintBody(raw: String): String {
    return BodyFormatterRegistry.prettyPrintBody(emptyMap(), raw)
}

/**
 * Reformats a compact JSON string into a human-readable indented form using [JsonBodyFormatter].
 */
fun prettyPrintJson(raw: String): String {
    return JsonBodyFormatter().prettyPrintJson(raw)
}
