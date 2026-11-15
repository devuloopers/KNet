package com.devuloopers.knet.data.desktop.importcapture

import kotlinx.serialization.json.*
import java.io.Reader
import java.io.Writer

/** Small strict streaming reader: HAR payload strings go directly to disk, metadata stays explicitly bounded. */
internal class StreamingCaptureJson(private val input: Reader, private val checkActive: () -> Unit) {
    private var next = input.read()
    private var work = 0
    private var metadataCharacters = 0
    private fun advance(): Int { val old = next; next = input.read(); if (++work % 8192 == 0) checkActive(); return old }
    private fun whitespace() { while (next == 32 || next == 9 || next == 10 || next == 13) advance() }
    fun expect(character: Char) { whitespace(); require(next == character.code) { "Malformed capture JSON." }; advance() }
    fun consume(character: Char): Boolean { whitespace(); if (next != character.code) return false; advance(); return true }
    fun end() { whitespace(); require(next == -1) { "Trailing capture JSON content." } }
    fun resetMetadataBudget() { metadataCharacters = 0 }

    fun string(output: Writer? = null): String {
        whitespace(); require(advance() == 34) { "Expected a JSON string." }
        val text = if (output == null) StringBuilder() else null
        var count = 0L
        while (true) {
            val token = advance()
            require(token >= 32) { "Unterminated or invalid JSON string." }
            if (token == 34) break
            val character = if (token != 92) token.toChar() else when (val escaped = advance()) {
                34, 47, 92 -> escaped.toChar()
                98 -> '\b'; 102 -> '\u000c'; 110 -> '\n'; 114 -> '\r'; 116 -> '\t'
                117 -> {
                    var value = 0
                    repeat(4) { val digit = advance().toChar().digitToIntOrNull(16); require(digit != null) { "Invalid JSON escape." }; value = value * 16 + digit }
                    value.toChar()
                }
                else -> error("Invalid JSON escape.")
            }
            require(++count <= 96L * 1024 * 1024) { "Capture string exceeds the payload limit." }
            if (text != null) {
                require(++metadataCharacters <= 1_048_576) { "Capture record metadata exceeds one mebibyte." }
                text.append(character)
            } else output!!.write(character.code)
        }
        return text?.toString().orEmpty()
    }

    fun value(path: List<String> = emptyList(), depth: Int = 0, payload: ((List<String>) -> Writer?)? = null): JsonElement {
        whitespace(); require(depth < 64) { "Capture JSON nesting exceeds the limit." }
        return when (next) {
            123 -> {
                expect('{'); val fields = linkedMapOf<String, JsonElement>()
                if (!consume('}')) do {
                    require(fields.size < 8192) { "Too many capture fields." }
                    val key = string(); require(key !in fields) { "Duplicate capture JSON field." }; expect(':')
                    fields[key] = value(path + key, depth + 1, payload)
                } while (consume(',')) else return JsonObject(fields)
                expect('}'); JsonObject(fields)
            }
            91 -> {
                expect('['); val items = mutableListOf<JsonElement>()
                if (!consume(']')) do {
                    require(items.size < 8192) { "Capture metadata array exceeds the limit." }
                    items += value(path + "*", depth + 1, payload)
                } while (consume(',')) else return JsonArray(items)
                expect(']'); JsonArray(items)
            }
            34 -> {
                val destination = payload?.invoke(path)
                if (destination == null) JsonPrimitive(string()) else { destination.use { string(it) }; JsonNull }
            }
            else -> {
                val token = StringBuilder()
                while (next != -1 && next !in listOf(32, 9, 10, 13, 44, 93, 125)) {
                    require(token.length < 128) { "Invalid capture scalar." }; token.append(advance().toChar())
                }
                require(token.isNotEmpty()) { "Missing capture JSON value." }
                require(token.toString() in setOf("true", "false", "null") || token.toString().matches(Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"))) { "Invalid JSON scalar." }
                val result = Json.parseToJsonElement(token.toString())
                require(result !is JsonObject && result !is JsonArray && (result !is JsonPrimitive || !result.isString)) { "Invalid capture scalar." }
                result
            }
        }
    }

    /** Walks the two HAR containers without materializing the entries array. Arbitrary key order is supported. */
    fun har(entry: (JsonObject, Map<String, java.io.File>) -> Unit, payloadFile: () -> java.io.File) {
        var foundLog = false; var foundEntries = false; var version: String? = null
        val rootKeys = mutableSetOf<String>()
        expect('{')
        if (!consume('}')) {
            do {
                val key = string(); require(rootKeys.add(key)) { "Duplicate HAR root field." }; expect(':')
                if (key != "log") value() else {
                    foundLog = true; expect('{'); val keys = mutableSetOf<String>()
                    if (!consume('}')) {
                        do {
                            val field = string(); require(keys.add(field)) { "Duplicate HAR log field." }; expect(':')
                            when (field) {
                                "version" -> version = string()
                                "entries" -> {
                                    foundEntries = true; expect('[')
                                    if (!consume(']')) {
                                        do {
                                            resetMetadataBudget(); val files = linkedMapOf<String, java.io.File>()
                                            try {
                                                val record = value(payload = { path ->
                                                    val name = path.joinToString(".")
                                                    if (name in setOf("request.postData.text", "response.content.text")) {
                                                        val file = payloadFile(); files[name] = file
                                                        java.io.OutputStreamWriter(file.outputStream(), Charsets.UTF_8.newEncoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)).buffered()
                                                    } else null
                                                }).jsonObject
                                                entry(record, files)
                                            } finally { files.values.forEach { it.delete() } }
                                        } while (consume(','))
                                        expect(']')
                                    }
                                }
                                else -> { resetMetadataBudget(); value() }
                            }
                        } while (consume(','))
                        expect('}')
                    }
                }
            } while (consume(','))
            expect('}')
        }
        end(); require(foundLog && foundEntries && version == "1.2") { "Expected a HAR 1.2 log with entries." }
    }
}
