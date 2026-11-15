package com.devuloopers.knet.application.usecase.traffic

import com.devuloopers.knet.application.contract.traffic.*
import com.devuloopers.knet.domain.clientNetwork.decoder.*
import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.traffic.model.body.*
import com.devuloopers.knet.traffic.model.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.net.URI
import kotlin.io.encoding.Base64

/** Shared bounds for every preset, including binary and machine-readable variants. */
internal object TrafficCopyLimits {
    const val TEXT = 4_194_304
    const val BODY = 65_536
    const val MESSAGE = 16_384
    const val MESSAGES = 32
    const val PART = 131_072
    const val TOO_LARGE = "The selected details exceed the clipboard limit. Select fewer items or use Export."
}

/** Exact canonical identity and bounded metadata, resolved and source-checked once per command. */
internal data class TrafficCopyRecord(val id: TrafficRecordId, val exchange: HttpExchangeSnapshot?, val opaque: OpaqueFlowSnapshot?, val ingress: TrafficConnectionIngress?) {
    val started: Long get() = exchange?.startedAtEpochMillis ?: opaque?.startedAtEpochMillis ?: -1
    val key: String get() = when (id) { is TrafficRecordId.Exchange -> id.id.value; is TrafficRecordId.Opaque -> id.id.value }
    fun weight(): Long = exchange?.let { e -> e.request.absoluteUrl().length.toLong() +
        (e.request.head.headers + e.request.trailers + e.response?.head?.headers.orEmpty() + e.response?.trailers.orEmpty()).sumOf { it.name.value.length.toLong() + it.value.length + 32 } + 256 } ?: 512L
}

/** Text and byte representations remain explicit; encoded variants preserve stored transport bytes. */
internal enum class TrafficCopyBodyFormat { TEXT, ORIGINAL_TEXT, BASE64, HEX }
internal data class TrafficCopyBody(val text: String? = null, val decoded: String? = null, val complete: Boolean = true, val note: String? = null)
internal data class TrafficCopyBytes(val bytes: ByteArray?, val complete: Boolean, val note: String?)

/** One bounded body read; callers hold the command's body-file lease throughout rendering. */
internal class TrafficCopyContent(private val bodies: BodyAccess, private val formatting: TrafficDetailsFormatting) {
    suspend fun raw(ref: MessageBodyRef, limit: Int = TrafficCopyLimits.BODY): TrafficCopyBytes {
        currentCoroutineContext().ensureActive()
        return when (ref) {
            MessageBodyRef.Empty -> TrafficCopyBytes(byteArrayOf(), true, null)
            is MessageBodyRef.Unavailable -> TrafficCopyBytes(null, false, "[Unavailable: ${TrafficCopySupport.safe(ref.outcome.toString())}]")
            is MessageBodyRef.Available -> {
                val chunk = try { bodies.readBody(ref.body.id, BodyRange(0, limit)) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
                if (chunk == null || chunk.offset != 0L || chunk.size > limit) TrafficCopyBytes(null, false, "[Unavailable: captured body could not be read]")
                else {
                    val complete = chunk.endOfBody && ref.body.outcome == BodyCaptureOutcome.Complete && chunk.size.toLong() == ref.body.storedBytes && ref.body.observedBytes == ref.body.storedBytes
                    TrafficCopyBytes(chunk.copyBytes(), complete, if (complete) null else "[Truncated: showing ${chunk.size} of ${ref.body.observedBytes} captured bytes]")
                }
            }
        }
    }

    suspend fun body(ref: MessageBodyRef, headers: List<Pair<String, String>>, limit: Int = TrafficCopyLimits.BODY,
        format: TrafficCopyBodyFormat = TrafficCopyBodyFormat.TEXT): TrafficCopyBody {
        if (ref == MessageBodyRef.Empty) return TrafficCopyBody()
        val raw = raw(ref, limit)
        val bytes = raw.bytes ?: return TrafficCopyBody(raw.note, complete = false, note = raw.note)
        if (format in setOf(TrafficCopyBodyFormat.BASE64, TrafficCopyBodyFormat.HEX)) {
            val text = if (format == TrafficCopyBodyFormat.BASE64) Base64.encode(bytes) else bytes.joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
            return TrafficCopyBody(listOfNotNull(text, raw.note).joinToString("\n"), complete = raw.complete, note = raw.note)
        }
        val encoding = (ref as? MessageBodyRef.Available)?.body?.contentEncoding
        val effectiveHeaders = if (headers.none { it.first.equals("Content-Encoding", true) } && encoding != null)
            headers + ("Content-Encoding" to encoding.token) else headers
        val decoded = BodyTextDecoder.decode(BodyDecoder.decode(bytes, effectiveHeaders, TrafficCopyLimits.BODY), effectiveHeaders)
        val text = when (decoded) {
            is DecodedTextResult.Success -> if (format == TrafficCopyBodyFormat.ORIGINAL_TEXT) decoded.text else formatting.json(decoded.text)
            is DecodedTextResult.BinaryKnownType -> "[Binary payload: ${TrafficCopySupport.observed(ref)} bytes; ${decoded.category.name}]"
            is DecodedTextResult.BinaryUnknownType -> "[Binary payload: ${TrafficCopySupport.observed(ref)} bytes]"
            is DecodedTextResult.UnsupportedEncoding -> "[Unsupported encoding: ${decoded.encoding}]"
            is DecodedTextResult.DecodingError -> "[Unable to decode payload: ${TrafficCopySupport.safe(decoded.errorMessage)}]"
        }
        return TrafficCopyBody(listOfNotNull(text.takeIf(String::isNotEmpty), raw.note).joinToString("\n").takeIf(String::isNotEmpty),
            (decoded as? DecodedTextResult.Success)?.text, raw.complete && decoded is DecodedTextResult.Success,
            if (decoded is DecodedTextResult.Success) raw.note else listOfNotNull(text, raw.note).joinToString("\n"))
    }
}

/** Bounded plain-text writer shared by full details and the smaller evidence presets. */
internal class TrafficCopyText {
    private val value = StringBuilder()
    fun append(text: String) { require(text.length.toLong() + value.length <= TrafficCopyLimits.TEXT) { TrafficCopyLimits.TOO_LARGE }; value.append(text) }
    fun line(text: String = "") { append(text); append("\n") }
    fun field(name: String, text: String, indent: String = "") { line("$indent${TrafficCopySupport.safe(name)}: ${TrafficCopySupport.safe(text)}") }
    fun part(name: String, text: String, indent: String = "  ") {
        line("$indent${TrafficCopySupport.safe(name)}:")
        bounded(text).replace("\r\n", "\n").replace('\r', '\n').lineSequence().forEach { line("$indent  ${it.replace("\u0000", "\\u0000")}") }
    }
    fun section(name: String, parts: List<Pair<String, String>>) { if (parts.isEmpty()) return; line(); line(TrafficCopySupport.safe(name)); parts.forEachIndexed { index, (label, content) -> if (index > 0) line(); part(label, content) } }
    fun literal(): String = value.toString()
    override fun toString(): String = value.toString().trimEnd() + "\n"
    companion object {
        fun bounded(text: String): String {
            if (text.length <= TrafficCopyLimits.PART) return text
            val end = if (text[TrafficCopyLimits.PART - 1].isHighSurrogate()) TrafficCopyLimits.PART - 1 else TrafficCopyLimits.PART
            return text.take(end) + "\n[Truncated: section exceeds clipboard display limit]"
        }
    }
}

/** Canonical address/field helpers shared across presets; values are never parsed from rendered output. */
internal object TrafficCopySupport {
    fun safe(value: String): String = value.replace("\r", "\\r").replace("\n", "\\n").replace("\u0000", "\\u0000")
    fun label(value: String): String = value.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    fun observed(ref: MessageBodyRef?): Long = (ref as? MessageBodyRef.Available)?.body?.observedBytes ?: 0
    fun size(bytes: Long): String = when { bytes >= 1_048_576 -> "${"%.2f".format(java.util.Locale.ROOT, bytes / 1_048_576.0)} MB"; bytes >= 1024 -> "${"%.2f".format(java.util.Locale.ROOT, bytes / 1024.0)} KB"; else -> "$bytes B" }
    fun destination(host: String, port: Int?): String = (if (host.contains(':') && !host.startsWith('[')) "[$host]" else host) + (port?.let { ":$it" } ?: "")
    fun host(request: HttpRequestSnapshot): String = when (val target = request.head.target) {
        is RequestTarget.Absolute -> destination(target.authority.host, target.authority.port ?: if (target.scheme.token.equals("https", true)) 443 else 80)
        is RequestTarget.AuthorityForm -> destination(target.authority.host, target.authority.port)
        else -> request.head.headers.firstOrNull { it.name.value.equals("host", true) }?.value ?: "Unavailable"
    }
    fun parameters(url: String): List<Pair<String, String>> = runCatching { URI(url).rawQuery }.getOrElse { url.substringAfter('?', "").substringBefore('#') }?.split('&')?.filter(String::isNotEmpty)?.map { raw -> decode(raw.substringBefore('=')) to decode(raw.substringAfter('=', "")) }.orEmpty()
    private fun decode(raw: String): String = runCatching { URI("http://copy.invalid/?${raw.replace("+", "%20")}").query }.getOrDefault(raw)
    fun pairs(values: List<Pair<String, String>>): String = values.joinToString("\n") { "${safe(it.first)}: ${safe(it.second)}" }
}
