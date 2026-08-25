package com.devuloopers.knet.engine.sse.protocol

/**
 * Shared bounded-memory policy used by SSE parsing, capture, API Studio, and breakpoints.
 *
 * @property maximumLineBytes Maximum raw bytes retained for one event-stream line.
 * @property maximumRecordBytes Maximum raw bytes retained for one complete SSE record.
 * @property maximumDataCharacters Maximum decoded characters retained across one record's data fields.
 * @property maximumEventTypeCharacters Maximum decoded event-type length.
 * @property maximumEventIdCharacters Maximum retained last-event-ID length.
 * @property maximumCapturedRecordsPerExchange Maximum child records captured for one HTTP exchange.
 * @property maximumCapturedBytesPerExchange Maximum combined child-record bytes captured for one exchange.
 * @property maximumRetainedApiStudioEvents Maximum live records retained by API Studio presentation.
 * @property maximumEditableRecordBytes Maximum record size admitted to breakpoint editing.
 * @property maximumContentEncodingLayers Maximum supported stacked HTTP Content-Encoding layers.
 * @property maximumDecoderInputBytesPerChunk Maximum encoded bytes copied into one decoder call.
 * @property maximumDecoderRetainedBytes Maximum encoded header/trailer bytes retained between decoder calls.
 * @property maximumDecodedBytesPerChunk Maximum decoded bytes emitted by one decoder call.
 * @property maximumDecoderExpansionRatio Maximum decoded-to-encoded ratio after the grace allowance.
 * @property maximumDecoderExpansionGraceBytes Decoded bytes allowed before ratio enforcement becomes strict.
 * @throws IllegalArgumentException if any limit is non-positive or a record cannot hold one maximum-sized line.
 */
data class SseLimits(
    val maximumLineBytes: Int = 64 * 1_024,
    val maximumRecordBytes: Int = 1_048_576,
    val maximumDataCharacters: Int = 1_048_576,
    val maximumEventTypeCharacters: Int = 1_024,
    val maximumEventIdCharacters: Int = 8 * 1_024,
    val maximumCapturedRecordsPerExchange: Int = 10_000,
    val maximumCapturedBytesPerExchange: Long = 64L * 1_024L * 1_024L,
    val maximumRetainedApiStudioEvents: Int = 1_000,
    val maximumEditableRecordBytes: Int = 1_048_576,
    val maximumContentEncodingLayers: Int = 2,
    val maximumDecoderInputBytesPerChunk: Int = 4 * 1_024 * 1_024,
    val maximumDecoderRetainedBytes: Int = 64 * 1_024,
    val maximumDecodedBytesPerChunk: Int = 4 * 1_024 * 1_024,
    val maximumDecoderExpansionRatio: Int = 256,
    val maximumDecoderExpansionGraceBytes: Int = 1_048_576,
) {
    init {
        require(maximumLineBytes > 0) { "Maximum SSE line bytes must be positive." }
        require(maximumRecordBytes >= maximumLineBytes) {
            "Maximum SSE record bytes must accommodate one maximum-sized line."
        }
        require(maximumDataCharacters > 0) { "Maximum SSE data characters must be positive." }
        require(maximumEventTypeCharacters > 0) { "Maximum SSE event type characters must be positive." }
        require(maximumEventIdCharacters > 0) { "Maximum SSE event ID characters must be positive." }
        require(maximumCapturedRecordsPerExchange > 0) { "Maximum captured SSE records must be positive." }
        require(maximumCapturedBytesPerExchange > 0L) { "Maximum captured SSE bytes must be positive." }
        require(maximumRetainedApiStudioEvents > 0) { "Maximum retained API Studio SSE events must be positive." }
        require(maximumEditableRecordBytes > 0) { "Maximum editable SSE record bytes must be positive." }
        require(maximumContentEncodingLayers > 0) { "Maximum SSE content-encoding layers must be positive." }
        require(maximumDecoderInputBytesPerChunk > 0) { "Maximum SSE decoder input bytes must be positive." }
        require(maximumDecoderRetainedBytes >= 10) { "Maximum SSE decoder retained bytes must hold a GZIP header." }
        require(maximumDecodedBytesPerChunk > 0) { "Maximum SSE decoded chunk bytes must be positive." }
        require(maximumDecoderExpansionRatio > 0) { "Maximum SSE decoder expansion ratio must be positive." }
        require(maximumDecoderExpansionGraceBytes > 0) { "SSE decoder expansion grace bytes must be positive." }
    }
}
