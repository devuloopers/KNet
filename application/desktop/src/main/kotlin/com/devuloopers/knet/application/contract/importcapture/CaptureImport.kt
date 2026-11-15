package com.devuloopers.knet.application.contract.importcapture

/** Supported content-identified interchange formats; file extensions never select a decoder. */
public enum class CaptureImportFormat { HAR, CHARLES_XML, KNET }

/** Explicit retained body availability; an absent capture is never represented as an empty body. */
public enum class ImportedBodyState { COMPLETE, EMPTY, UNAVAILABLE, TRUNCATED, REDACTED, OMITTED, MISSING, UNSUPPORTED }

/** File-owned historical capture, isolated from authenticated device identities and live policy. */
public data class ImportedCapture(
    /** Opaque process-owned storage handle. */ public val id: String,
    /** Original display filename, never a storage path. */ public val name: String,
    /** Content-verified format. */ public val format: CaptureImportFormat,
    /** Exact number of retained parent and protocol records. */ public val records: Long,
    /** Bodies whose bytes are not complete. */ public val unavailableBodies: Long,
    /** Bounded format/retention explanations. */ public val warnings: List<String>,
)

/** A file-local payload handle; callers receive bounded previews, never private filesystem paths. */
public data class ImportedBody(
    /** Opaque body key, or null for unavailable/empty content. */ public val id: String?,
    /** Capture availability from the source. */ public val state: ImportedBodyState,
    /** Actual retained bytes. */ public val bytes: Long,
    /** True when the interchange contains decoded content despite original compression headers. */ public val decoded: Boolean,
    /** Source payload is a decoded text string serialized to UTF-8 by the interchange. */ public val utf8Text: Boolean = false,
)

/** Ordered duplicate headers and independent payload availability for a captured HTTP message. */
public data class ImportedMessage(
    /** Headers in original order. */ public val headers: List<Pair<String, String>>,
    /** Payload metadata. */ public val body: ImportedBody,
)

/** One bounded historical row with optional HTTP messages and original structured metadata. */
public data class ImportedRecord(
    /** Zero-based immutable position within this file. */ public val index: Long,
    /** Original protocol/record-kind token; unknown extensions remain inspectable. */ public val kind: String,
    /** Captured method, if this is HTTP. */ public val method: String?,
    /** Captured destination or URL, never a connection instruction. */ public val destination: String,
    /** Captured HTTP status, if available. */ public val status: Int?,
    /** Epoch timestamp if present in the source. */ public val startedAt: Long?,
    /** Total milliseconds if present in the source. */ public val durationMillis: Double?,
    /** HTTP request metadata, when present. */ public val request: ImportedMessage?,
    /** HTTP response metadata, when present. */ public val response: ImportedMessage?,
    /** Bounded JSON metadata for transparent source inspection. */ public val metadataJson: String,
    /** Native protocol frame/event body, independent of HTTP messages. */ public val payload: ImportedMessage? = null,
)

/** Work completed so far; cancellation remains available during parsing and payload copying. */
public data class CaptureImportProgress(public val records: Long, public val payloadBytes: Long)

/** Explicit bounded payload preview; truncation does not change the retained body. */
public data class ImportedBodyPreview(public val bytes: ByteArray, public val truncated: Boolean)

/** Native open dialog boundary; cancelling leaves existing imported/live captures untouched. */
public fun interface CaptureImportFilePicker {
    /** Returns a selected path or null. No network resource fetching is supported. */
    public suspend fun choose(): String?
}

/** Background bounded parsing, private disk spooling and indexed historical access. */
public interface CaptureImportRepository {
    /** Parses atomically, rolling back this task's private files on failure or cancellation. */
    public suspend fun open(path: String, progress: (CaptureImportProgress) -> Unit): ImportedCapture
    /** Reads at most 128 metadata records without retaining an entire capture in presentation. */
    public suspend fun page(id: String, offset: Long, limit: Int = 128): List<ImportedRecord>
    /** Retrieves one selected record with headers and structured metadata. */
    public suspend fun record(id: String, index: Long): ImportedRecord
    /** Reads a bounded prefix for inspection; unavailable payloads return no invented bytes. */
    public suspend fun body(id: String, body: ImportedBody, limit: Int = 1_048_576): ImportedBodyPreview
    /** Deletes only this import's private spool after its UI owner closes it. */
    public suspend fun close(id: String)
}
