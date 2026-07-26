package com.devuloopers.knet.bodyformatter.formatter

import com.devuloopers.knet.bodyformatter.model.BodyFormat
import java.net.URLDecoder

/**
 * Strategy formatter for `application/x-www-form-urlencoded` payloads.
 */
class FormDataBodyFormatter : BodyFormatter {
    override val priority: Int = 60

    override fun matches(headers: Map<String, String>, bodyText: String): Boolean {
        val contentType = headers.entries.find { it.key.equals("content-type", ignoreCase = true) }?.value ?: ""
        val mime = contentType.substringBefore(";").trim().lowercase()
        val trimmed = bodyText.trim()

        return mime.contains("x-www-form-urlencoded") || (trimmed.contains("&") && trimmed.contains("="))
    }

    override fun format(headers: Map<String, String>, bodyText: String): BodyFormat {
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
}
