package com.devuloopers.knet.ui.apistudio.model

import com.devuloopers.knet.bodyformatter.formatter.BodyFormatterRegistry
import com.devuloopers.knet.bodyformatter.model.BodyFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background builder responsible for transforming raw HTTP response outputs into immutable
 * [ResponsePresentation] UI models.
 *
 * Runs 100% on [Dispatchers.Default] off the UI thread to ensure zero format resolution,
 * JSON/XML pretty-printing, or cookie parsing work occurs during Compose Multiplatform composition frames.
 */
object ResponsePresentationBuilder {

    /**
     * Builds an immutable [ResponsePresentation] model from raw response headers and body text.
     * Guaranteed to return a valid presentation model even if formatting fails (falls back to raw text).
     *
     * @param headers HTTP response headers map.
     * @param bodyText Raw HTTP response body string.
     * @return Fully pre-computed [ResponsePresentation] model.
     */
    suspend fun build(
        headers: Map<String, String>,
        bodyText: String
    ): ResponsePresentation = withContext(Dispatchers.Default) {
        if (bodyText.isBlank()) {
            return@withContext ResponsePresentation(
                rawBody = bodyText,
                formattedBody = bodyText,
                bodyFormat = BodyFormat.RawText(""),
                cookies = parseCookies(headers),
                lineCount = 0,
                characterCount = 0
            )
        }

        try {
            val format = BodyFormatterRegistry.resolveFormat(headers, bodyText)
            val pretty = BodyFormatterRegistry.prettyPrintBody(headers, bodyText)
            val lines = countLines(pretty)
            val cookies = parseCookies(headers)

            ResponsePresentation(
                rawBody = bodyText,
                formattedBody = pretty,
                bodyFormat = format,
                cookies = cookies,
                lineCount = lines,
                characterCount = pretty.length
            )
        } catch (_: Exception) {
            val fallbackFormat = BodyFormat.RawText(bodyText)
            ResponsePresentation(
                rawBody = bodyText,
                formattedBody = bodyText,
                bodyFormat = fallbackFormat,
                cookies = parseCookies(headers),
                lineCount = countLines(bodyText),
                characterCount = bodyText.length
            )
        }
    }

    /**
     * Extracts and parses HTTP Set-Cookie response headers into structured [ResponseCookieItem] models.
     */
    private fun parseCookies(headers: Map<String, String>): List<ResponseCookieItem> {
        if (headers.isEmpty()) return emptyList()
        return headers.entries
            .filter { it.key.equals("set-cookie", ignoreCase = true) || it.key.equals("cookie", ignoreCase = true) }
            .flatMap { entry ->
                entry.value.split("\n", ",").mapNotNull { rawCookie ->
                    parseSingleCookie(rawCookie.trim())
                }
            }
    }

    private fun parseSingleCookie(raw: String): ResponseCookieItem? {
        if (raw.isBlank() || !raw.contains("=")) return null
        val parts = raw.split(";")
        val nameValue = parts.firstOrNull()?.split("=", limit = 2) ?: return null
        if (nameValue.size < 2) return null

        val name = nameValue[0].trim()
        val value = nameValue[1].trim()
        var domain = ""
        var path = ""
        var isSecure = false
        var isHttpOnly = false

        parts.drop(1).forEach { attr ->
            val trimmedAttr = attr.trim()
            when {
                trimmedAttr.startsWith("Domain=", ignoreCase = true) -> domain = trimmedAttr.substringAfter("=").trim()
                trimmedAttr.startsWith("Path=", ignoreCase = true) -> path = trimmedAttr.substringAfter("=").trim()
                trimmedAttr.equals("Secure", ignoreCase = true) -> isSecure = true
                trimmedAttr.equals("HttpOnly", ignoreCase = true) -> isHttpOnly = true
            }
        }

        return ResponseCookieItem(
            name = name,
            value = value,
            domain = domain,
            path = path,
            isSecure = isSecure,
            isHttpOnly = isHttpOnly
        )
    }

    private fun countLines(text: String): Int {
        if (text.isEmpty()) return 0
        var count = 1
        for (i in text.indices) {
            if (text[i] == '\n') count++
        }
        return count
    }
}
