package com.devuloopers.knet.bodyformatter.formatter

import com.devuloopers.knet.bodyformatter.model.BodyFormat

/**
 * Strategy formatter for Image MIME types (PNG, JPEG, SVG, WebP, GIF).
 */
class ImageBodyFormatter : BodyFormatter {
    override val priority: Int = 95

    override fun matches(headers: Map<String, String>, bodyText: String): Boolean {
        val contentType = headers.entries.find { it.key.equals("content-type", ignoreCase = true) }?.value ?: ""
        val mime = contentType.substringBefore(";").trim().lowercase()
        return mime.startsWith("image/")
    }

    override fun format(headers: Map<String, String>, bodyText: String): BodyFormat {
        val contentType = headers.entries.find { it.key.equals("content-type", ignoreCase = true) }?.value ?: ""
        val mime = contentType.substringBefore(";").trim().lowercase()
        val label = when {
            mime.contains("png") -> "PNG Image"
            mime.contains("jpeg") || mime.contains("jpg") -> "JPEG Image"
            mime.contains("gif") -> "GIF Image"
            mime.contains("svg") -> "SVG Image"
            mime.contains("webp") -> "WebP Image"
            else -> "Image"
        }
        return BodyFormat.Image(label)
    }
}
