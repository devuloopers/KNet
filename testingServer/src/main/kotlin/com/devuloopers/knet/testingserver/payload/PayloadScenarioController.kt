package com.devuloopers.knet.testingserver.payload

import com.devuloopers.knet.testingserver.grpc.v1.EchoRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.reactor.awaitSingle
import org.msgpack.jackson.dataformat.MessagePackFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.FilePart
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import kotlin.time.Duration.Companion.milliseconds

/** Nested object used to exercise structured JSON, CBOR, and MessagePack rendering. */
data class StructuredPayloadDetails(
    val enabled: Boolean,
    val tags: List<String>,
)

/**
 * Deterministic structured payload shared by equivalent serialization formats.
 *
 * @property id Stable fixture identifier.
 * @property message Human-readable fixture message.
 * @property details Nested structure used to verify formatter indentation and types.
 */
data class StructuredPayload(
    val id: Int,
    val message: String,
    val details: StructuredPayloadDetails,
)

/**
 * One independently framed newline-delimited JSON value.
 *
 * @property sequence One-based record order.
 * @property message Deterministic record body.
 */
data class NdjsonRecord(
    val sequence: Int,
    val message: String,
)

/**
 * Multipart metadata returned after every request part has been consumed.
 *
 * @property name Form field name.
 * @property filename Client-provided filename for file parts.
 * @property contentType Declared media type when present.
 */
data class MultipartPartSummary(
    val name: String,
    val filename: String?,
    val contentType: String?,
)

/** Provides representative textual, structured, framed, and binary HTTP payloads. */
@RestController
@RequestMapping("/lab/v1/payload")
class PayloadScenarioController {
    private val cborMapper = ObjectMapper(CBORFactory())
    private val messagePackMapper = ObjectMapper(MessagePackFactory())

    /** @return Nested JSON object used for ordinary formatter and inspector validation. */
    @GetMapping("/json", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun json(): StructuredPayload = structuredPayload()

    /**
     * Emits independently flushable JSON records separated by newlines.
     *
     * @param count Record count clamped to one through one hundred.
     * @param delayMillis Optional non-blocking delay between records.
     * @return Cold bounded NDJSON stream.
     */
    @GetMapping("/ndjson", produces = [MediaType.APPLICATION_NDJSON_VALUE])
    fun ndjson(
        @RequestParam(defaultValue = "3") count: Int,
        @RequestParam(defaultValue = "0") delayMillis: Long,
    ): Flow<NdjsonRecord> = flow {
        val effectiveCount = count.coerceIn(1, MAX_STREAM_RECORDS)
        val effectiveDelay = delayMillis.coerceIn(0L, MAX_STREAM_DELAY_MILLIS)
        repeat(effectiveCount) { index ->
            emit(NdjsonRecord(sequence = index + 1, message = "ndjson-record-${index + 1}"))
            if (effectiveDelay > 0L && index + 1 < effectiveCount) {
                delay(effectiveDelay.milliseconds)
            }
        }
    }

    /** @return Resource-backed XML document. */
    @GetMapping("/xml", produces = [MediaType.APPLICATION_XML_VALUE])
    fun xml(): ResponseEntity<ByteArray> = resourceResponse("fixtures/sample.xml", MediaType.APPLICATION_XML)

    /** @return Resource-backed SOAP 1.2 envelope. */
    @GetMapping("/soap", produces = [SOAP_MEDIA_TYPE_VALUE])
    fun soap(): ResponseEntity<ByteArray> = resourceResponse("fixtures/sample-soap.xml", SOAP_MEDIA_TYPE)

    /** @return UTF-8 plain-text fixture. */
    @GetMapping("/text", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun text(): String = "KNet local protocol lab plain-text payload."

    /**
     * Returns a bounded text body large enough to exercise capture thresholds and compression.
     *
     * @param bytes Requested character count, clamped to five megabytes.
     * @return Deterministically generated text payload.
     */
    @GetMapping("/large-text", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun largeText(@RequestParam(defaultValue = "1024") bytes: Int): String =
        "K".repeat(bytes.coerceIn(1, MAX_LARGE_PAYLOAD_BYTES))

    /**
     * Returns deterministic octets including zero and non-UTF-8 values.
     *
     * @param bytes Requested byte count, clamped to the binary fixture limit.
     * @return Generated binary payload.
     */
    @GetMapping("/binary", produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    fun binary(@RequestParam(defaultValue = "256") bytes: Int): ByteArray =
        ByteArray(bytes.coerceIn(1, MAX_BINARY_PAYLOAD_BYTES)) { index -> index.toByte() }

    /** @return Structured fixture encoded as CBOR. */
    @GetMapping("/cbor", produces = [CBOR_MEDIA_TYPE_VALUE])
    fun cbor(): ByteArray = cborMapper.writeValueAsBytes(structuredPayload())

    /** @return Structured fixture encoded as MessagePack. */
    @GetMapping("/messagepack", produces = [MESSAGE_PACK_MEDIA_TYPE_VALUE])
    fun messagePack(): ByteArray = messagePackMapper.writeValueAsBytes(structuredPayload())

    /** @return Generated protocol-buffer request encoded with the canonical protobuf wire format. */
    @GetMapping("/protobuf", produces = [PROTOBUF_MEDIA_TYPE_VALUE])
    fun protobuf(): ByteArray = EchoRequest.newBuilder()
        .setMessage("KNet protobuf payload")
        .build()
        .toByteArray()

    /**
     * Consumes and returns URL-encoded form fields while preserving repeated values.
     *
     * @param exchange WebFlux exchange containing form data.
     * @return Submitted form field values.
     */
    @PostMapping("/form", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    suspend fun form(exchange: ServerWebExchange): Map<String, List<String>> = exchange.formData
        .awaitSingle()
        .mapValues { (_, values) -> values.toList() }

    /**
     * Consumes multipart request content and returns safe part metadata.
     *
     * @param exchange WebFlux exchange containing multipart data.
     * @return One metadata record per submitted part.
     */
    @PostMapping("/multipart", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun multipart(exchange: ServerWebExchange): List<MultipartPartSummary> = exchange.multipartData
        .awaitSingle()
        .values
        .flatten()
        .map { part ->
            MultipartPartSummary(
                name = part.name(),
                filename = (part as? FilePart)?.filename(),
                contentType = part.headers().contentType?.toString(),
            )
        }

    private fun structuredPayload(): StructuredPayload = StructuredPayload(
        id = 1,
        message = "KNet local protocol lab",
        details = StructuredPayloadDetails(
            enabled = true,
            tags = listOf("capture", "format", "inspection"),
        ),
    )

    private fun resourceResponse(path: String, mediaType: MediaType): ResponseEntity<ByteArray> {
        val bytes = ClassPathResource(path).inputStream.use { input -> input.readBytes() }
        return ResponseEntity.ok().contentType(mediaType).body(bytes)
    }

    private companion object {
        const val MAX_STREAM_RECORDS = 100
        const val MAX_STREAM_DELAY_MILLIS = 10_000L
        const val MAX_LARGE_PAYLOAD_BYTES = 5 * 1024 * 1024
        const val MAX_BINARY_PAYLOAD_BYTES = 1024 * 1024
        const val SOAP_MEDIA_TYPE_VALUE = "application/soap+xml"
        const val CBOR_MEDIA_TYPE_VALUE = "application/cbor"
        const val MESSAGE_PACK_MEDIA_TYPE_VALUE = "application/msgpack"
        const val PROTOBUF_MEDIA_TYPE_VALUE = "application/x-protobuf"
        val SOAP_MEDIA_TYPE: MediaType = MediaType.parseMediaType(SOAP_MEDIA_TYPE_VALUE)
    }
}
