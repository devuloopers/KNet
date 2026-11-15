package com.devuloopers.knet.data.desktop.importcapture

import kotlinx.serialization.json.*
import java.io.*
import java.net.URI
import kotlin.io.encoding.Base64
import kotlin.io.encoding.decodingWith
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants.*

/** Charles' published session DTD, streamed with external entities and DTD expansion disabled. */
internal object CharlesCaptureXml {
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    fun read(source: File, spool: ImportSpool) {
        val factory = XMLInputFactory.newFactory().apply {
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
            setProperty("javax.xml.stream.isSupportingExternalEntities", false)
            setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false)
            xmlResolver = javax.xml.stream.XMLResolver { _, _, _, _ -> ByteArrayInputStream(byteArrayOf()) }
        }
        source.inputStream().buffered().use { input ->
            val reader = factory.createXMLStreamReader(input)
            val stack = mutableListOf<String>(); var root = false
            var transaction = emptyMap<String, String>(); var section: String? = null
            val messages = mutableMapOf<String, JsonObject>()
            var headers = mutableListOf<JsonElement>(); var sectionAttributes = emptyMap<String, String>()
            var name = ""; var text = StringBuilder(); var textBudget = 0
            var temporary: File? = null; var bodyWriter: Writer? = null; var bodyEncoding = ""; var body: JsonObject? = null
            fun attributes(): Map<String, String> = buildMap {
                require(reader.attributeCount <= 64)
                repeat(reader.attributeCount) { i -> val value = reader.getAttributeValue(i); require(value.length < 65_536); put(reader.getAttributeLocalName(i), value) }
            }
            try {
                while (reader.hasNext()) {
                    spool.checkActive()
                    when (reader.next()) {
                        START_ELEMENT -> {
                            val element = reader.localName
                            if (stack.isEmpty()) { require(element == "charles-session" && !root) { "Expected a Charles session XML file." }; root = true }
                            require(stack.size < 32); stack += element
                            when (element) {
                                "transaction" -> { require(stack.size == 2); transaction = attributes(); messages.clear(); textBudget = 0 }
                                "request", "response" -> { require(stack.size == 3 && stack[1] == "transaction"); section = element; sectionAttributes = attributes(); headers = mutableListOf(); body = null }
                                "header" -> { require(headers.size < 8192); name = "" }
                                "body" -> {
                                    require(section != null && stack.size == 4); bodyEncoding = attributes()["encoding"].orEmpty()
                                    temporary = spool.temporaryPayload()
                                    bodyWriter = OutputStreamWriter(temporary!!.outputStream(), Charsets.UTF_8.newEncoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)).buffered()
                                }
                            }
                            text = StringBuilder()
                        }
                        CHARACTERS, CDATA -> {
                            if (bodyWriter != null) {
                                require(temporary!!.length() + reader.textLength * 3L <= 96L * 1024 * 1024) { "Charles payload exceeds the import limit." }
                                bodyWriter!!.write(reader.textCharacters, reader.textStart, reader.textLength)
                            } else {
                                textBudget += reader.textLength; require(textBudget <= 1_048_576) { "Charles metadata exceeds the record limit." }
                                text.append(reader.textCharacters, reader.textStart, reader.textLength)
                            }
                        }
                        ENTITY_REFERENCE -> error("XML entity references are unsupported in capture files.")
                        END_ELEMENT -> {
                            val element = reader.localName
                            when (element) {
                                "name" -> name = text.toString()
                                "value" -> headers += buildJsonObject { put("name", name); put("value", text.toString()) }
                                "body" -> {
                                    bodyWriter?.close(); bodyWriter = null
                                    body = when (bodyEncoding.lowercase()) {
                                        "base64", "text", "plain", "" -> temporary!!.inputStream().buffered().use { raw ->
                                            spool.payload(if (bodyEncoding.equals("base64", true)) WhitespaceSkippingInputStream(raw).decodingWith(Base64.Default) else raw, "COMPLETE", true, utf8Text = !bodyEncoding.equals("base64", true))
                                        }
                                        else -> buildJsonObject { put("state", "UNSUPPORTED"); put("storedBytes", 0) }
                                    }
                                    temporary?.delete(); temporary = null
                                }
                                "request", "response" -> {
                                    val currentBody = body ?: buildJsonObject { put("state", if (sectionAttributes["body"] == "false") "EMPTY" else "UNAVAILABLE"); put("storedBytes", 0) }
                                    messages[element] = buildJsonObject {
                                        put("headers", JsonArray(headers)); put("body", currentBody)
                                        put("protocol", transaction["protocolVersion"] ?: "Unknown")
                                        if (element == "response") sectionAttributes["status"]?.toIntOrNull()?.let { put("status", it) }
                                    }; section = null
                                }
                                "transaction" -> {
                                    val scheme = transaction["protocol"].orEmpty().lowercase(); require(scheme in setOf("http", "https")) { "Unsupported Charles transaction protocol." }
                                    val host = requireNotNull(transaction["host"]); val port = transaction["port"]?.toIntOrNull(); require(port == null || port in 1..65535)
                                    val target = transaction["path"].orEmpty().ifEmpty { "/" } + transaction["query"]?.takeIf { it.isNotEmpty() }?.let { if (it.startsWith('?')) it else "?$it" }.orEmpty()
                                    val authority = if (':' in host && !host.startsWith('[')) "[$host]" else host
                                    val url = "$scheme://$authority${port?.let { ":$it" }.orEmpty()}$target"; require(URI(url).host != null)
                                    val request = requireNotNull(messages["request"])
                                    spool.record(buildJsonObject {
                                        put("kind", "http"); put("id", "import-${spool.records}")
                                        transaction["startTimeMillis"]?.toLongOrNull()?.let { put("startedAt", it) }
                                        put("request", JsonObject(request + mapOf("method" to JsonPrimitive(requireNotNull(transaction["method"])), "url" to JsonPrimitive(url))))
                                        messages["response"]?.let { put("response", it) }
                                        put("timings", buildJsonObject {
                                            val start = transaction["startTimeMillis"]?.toLongOrNull(); val end = transaction["endTimeMillis"]?.toLongOrNull()
                                            if (start != null && end != null && end >= start) put("total", end - start)
                                        })
                                        put("originalCharles", buildJsonObject { transaction.forEach { (key, value) -> put(key, value) } })
                                    })
                                }
                            }
                            require(stack.lastOrNull() == element); stack.removeLast(); text = StringBuilder()
                        }
                    }
                }
                require(root && stack.isEmpty()) { "Incomplete Charles XML." }
            } finally { bodyWriter?.close(); temporary?.delete(); reader.close() }
        }
    }
}

/** XML exporters may wrap base64 lines; ignore only ASCII whitespace and reject every other invalid symbol. */
private class WhitespaceSkippingInputStream(input: InputStream) : FilterInputStream(input) {
    override fun read(): Int {
        var value = super.read()
        while (value == 32 || value == 9 || value == 10 || value == 13) value = super.read()
        return value
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        var count = 0
        while (count < length) { val value = read(); if (value < 0) return if (count == 0) -1 else count; buffer[offset + count++] = value.toByte() }
        return count
    }
}
