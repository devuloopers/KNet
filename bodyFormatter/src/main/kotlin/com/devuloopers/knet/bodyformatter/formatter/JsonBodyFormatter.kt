package com.devuloopers.knet.bodyformatter.formatter

import com.devuloopers.knet.bodyformatter.model.BodyFormat
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@OptIn(ExperimentalSerializationApi::class)
private val strictJsonSerializer = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    isLenient = true
    ignoreUnknownKeys = true
}

/**
 * Strategy formatter for JSON objects, arrays, and Google XSSI security prefixed payloads.
 */
class JsonBodyFormatter : BodyFormatter {
    override val priority: Int = 70

    override fun matches(headers: Map<String, String>, bodyText: String): Boolean {
        val contentType = headers.entries.find { it.key.equals("content-type", ignoreCase = true) }?.value ?: ""
        val mime = contentType.substringBefore(";").trim().lowercase()
        val trimmed = bodyText.trim()

        var inspectBody = trimmed
        for (prefix in listOf(")]}'", "while(1);", "for(;;);", "{}&&")) {
            if (inspectBody.startsWith(prefix)) {
                inspectBody = inspectBody.substring(prefix.length).trim()
                break
            }
        }

        if (mime.contains("json")) return true
        if (inspectBody.startsWith("{") || (inspectBody.startsWith("[") && !inspectBody.contains("[["))) return true

        val formatted = prettyPrintJson(trimmed)
        return formatted != trimmed
    }

    override fun format(headers: Map<String, String>, bodyText: String): BodyFormat {
        val trimmed = bodyText.trim()
        if (trimmed.lines().size > 1 && trimmed.lines().filter { it.trim().startsWith("{") }.size > 1) {
            val frames = trimmed.lines().filter { it.trim().isNotEmpty() }.map { prettyPrintJson(it) }
            return BodyFormat.JsonStream(frames)
        }
        val formattedText = prettyPrintJson(trimmed)
        return BodyFormat.Json(formattedText)
    }

    fun prettyPrintJson(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("[Binary payload") || trimmed.startsWith("[Binary")) return raw

        val firstObjIndex = trimmed.indexOf('{')
        val firstArrIndex = trimmed.indexOf('[')

        val startIndex = when {
            firstObjIndex != -1 && firstArrIndex != -1 -> minOf(firstObjIndex, firstArrIndex)
            firstObjIndex != -1 -> firstObjIndex
            firstArrIndex != -1 -> firstArrIndex
            else -> return raw
        }

        val lastObjIndex = trimmed.lastIndexOf('}')
        val lastArrIndex = trimmed.lastIndexOf(']')
        val endIndex = maxOf(lastObjIndex, lastArrIndex)

        if (endIndex <= startIndex) return raw

        val targetJson = trimmed.substring(startIndex, endIndex + 1)

        return try {
            val jsonElement = strictJsonSerializer.parseToJsonElement(targetJson)
            strictJsonSerializer.encodeToString(JsonElement.serializer(), jsonElement)
        } catch (_: Exception) {
            formatCustomJsonFallback(targetJson)
        }
    }

    private fun formatCustomJsonFallback(targetJson: String): String {
        val sb = StringBuilder()
        var indent = 0
        var inString = false
        var escaped = false
        for (ch in targetJson) {
            when {
                escaped -> { sb.append(ch); escaped = false }
                inString && ch == '\\' -> { sb.append(ch); escaped = true }
                ch == '"' -> { inString = !inString; sb.append(ch) }
                inString -> sb.append(ch)
                ch == '{' || ch == '[' -> {
                    sb.append(ch); sb.append('\n'); indent++
                    repeat(indent) { sb.append("  ") }
                }
                ch == '}' || ch == ']' -> {
                    sb.append('\n'); indent--
                    repeat(indent) { sb.append("  ") }
                    sb.append(ch)
                }
                ch == ',' -> {
                    sb.append(ch); sb.append('\n')
                    repeat(indent) { sb.append("  ") }
                }
                ch == ':' -> sb.append(": ")
                ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t' -> { /* normalise */ }
                else -> sb.append(ch)
            }
        }
        return sb.toString()
            .replace("""\{\n\s*\}""".toRegex(), "{}")
            .replace("""\[\n\s*]""".toRegex(), "[]")
    }
}
