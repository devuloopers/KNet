package com.devuloopers.knet.engine.formatter.formatters

import com.devuloopers.knet.engine.formatter.BodyFormatter
import com.devuloopers.knet.engine.formatter.model.BodyFormat
import java.net.URLDecoder

/**
 * Strategy formatter for `application/x-www-form-urlencoded` and `multipart/form-data` payloads.
 */
class FormDataBodyFormatter : BodyFormatter {
    override val priority: Int = 60

    override fun matches(headers: Map<String, String>, bodyText: String): Boolean {
        val contentType = headers.entries.find { it.key.equals("content-type", ignoreCase = true) }?.value ?: ""
        val mime = contentType.substringBefore(";").trim().lowercase()
        val trimmed = bodyText.trim()

        return mime.contains("x-www-form-urlencoded") || mime.contains("multipart/form-data") ||
            (trimmed.contains("&") && trimmed.contains("="))
    }

    override fun format(headers: Map<String, String>, bodyText: String): BodyFormat {
        val contentType = headers.entries.find { it.key.equals("content-type", ignoreCase = true) }?.value ?: ""
        val mime = contentType.substringBefore(";").trim().lowercase()

        if (mime.contains("multipart/form-data")) {
            return formatMultipart(bodyText, contentType)
        }

        val pairs = bodyText.trim().split("&").filter { it.contains("=") }.map { pair ->
            val parts = pair.split("=", limit = 2)
            val key = parts[0]
            val rawValue = parts.getOrNull(1) ?: ""
            val decodedValue = try {
                URLDecoder.decode(rawValue, Charsets.UTF_8.name())
            } catch (_: Exception) {
                rawValue
            }
            key to decodedValue
        }
        return BodyFormat.FormData(pairs)
    }

    private fun formatMultipart(bodyText: String, contentType: String): BodyFormat {
        val boundary = extractBoundary(contentType) ?: return BodyFormat.RawText(bodyText)
        val boundaryMarker = "--$boundary"
        val normalized = bodyText.replace("\r\n", "\n").replace("\r", "\n")
        val parts = normalized.split(boundaryMarker)
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "--" }

        val pairs = mutableListOf<Pair<String, String>>()
        for (part in parts) {
            val cleaned = part.removePrefix("--").trim()
            if (cleaned.isEmpty()) continue

            val headerSeparator = cleaned.indexOf("\n\n")
            val headersText = if (headerSeparator >= 0) cleaned.substring(0, headerSeparator) else ""
            val payload = if (headerSeparator >= 0) cleaned.substring(headerSeparator + 2) else cleaned

            val dispositionLine = headersText.lines().firstOrNull { it.contains("Content-Disposition", ignoreCase = true) } ?: ""
            val nameMatch = Regex("""name="([^"]+)"""").find(dispositionLine)
            val filenameMatch = Regex("""filename="([^"]+)"""").find(dispositionLine)

            val name = nameMatch?.groupValues?.getOrNull(1) ?: "part"
            val filename = filenameMatch?.groupValues?.getOrNull(1)
            val label = if (filename != null) "$name [$filename]" else name
            val value = payload.trim().removeSuffix("\n")
            pairs.add(label to value)
        }

        return BodyFormat.FormData(pairs)
    }

    private fun extractBoundary(contentType: String): String? {
        val boundaryPrefix = "boundary="
        val boundaryValue = contentType.substringAfter(boundaryPrefix, "")
            .substringBefore(";")
            .trim()
            .removeSurrounding("\"")
        return boundaryValue.ifEmpty { null }
    }
}
