package com.devuloopers.knet.application.contract.export

import com.devuloopers.knet.application.contract.traffic.TrafficPageQuery
import com.devuloopers.knet.application.contract.traffic.TrafficRecordId
import com.devuloopers.knet.application.contract.apistudio.*
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutionResponseHead
import com.devuloopers.knet.domain.clientNetwork.model.ExecutionResult
import com.devuloopers.knet.domain.clientNetwork.model.OutboundRequestBody
import kotlinx.coroutines.flow.StateFlow

/** Portable file choices; native archives retain framed and opaque evidence that HAR cannot express. */
public enum class CaptureExportFormat(public val label: String, public val extension: String) {
    KNET("KNet Capture", "knetcapture"), HAR("HTTP Archive (HAR)", "har"),
}

/** Immutable source captured by the invoking screen; later tab changes cannot redirect it. */
public sealed interface CaptureExportSource {
    /** Every stored result matching this bounded-query definition, independent of the UI page. */
    public data class Traffic(public val query: TrafficPageQuery) : CaptureExportSource
    /** One canonical item, resolved without trusting its display text. */
    public data class Item(public val id: TrafficRecordId) : CaptureExportSource
    /** Exact ordered selection frozen at invocation; every retained item must belong to the original source. */
    public data class Selection(public val records: List<TrafficRecordId>, public val source: com.devuloopers.knet.application.contract.traffic.TrafficSource) : CaptureExportSource {
        init { require(records.isNotEmpty() && records.size <= 10_000 && records.distinct().size == records.size) }
    }
    /** One immutable API Studio execution owner; unrelated Traffic admission is immaterial. */
    public data class Studio(public val id: String) : CaptureExportSource
}

/** Explicit sharing options; header credential removal does not claim to scrub arbitrary body secrets. */
public data class CaptureExportOptions(
    public val format: CaptureExportFormat = CaptureExportFormat.KNET,
    public val includePayloads: Boolean = true,
    public val includeCredentials: Boolean = false,
)

/** Frozen private spool handle and exact compatibility counts, safe to hold while the save dialog is open. */
public data class PreparedCaptureExport(
    public val id: String,
    public val records: Long,
    public val httpExchanges: Long,
    public val messages: Long,
    public val missingPayloads: Long,
    public val snapshotEpochMillis: Long,
    /** Selected identities removed before preparation; never silently replaced by other rows. */
    public val unavailableRecords: Long = 0,
)

/** Chunk-bound progress, never a guessed percentage. */
public data class CaptureExportProgress(public val recordsWritten: Long, public val bytesWritten: Long)

/** Native destination selection; the platform owns its overwrite confirmation. */
public fun interface CaptureExportFilePicker {
    /** Returns null for a cancelled save dialog. */
    public suspend fun choose(format: CaptureExportFormat): String?
}

/** Adapter boundary for private snapshot spooling and atomic, cancellable archive serialization. */
public interface CaptureExportRepository {
    /** Freezes metadata and available payloads before the user selects a destination. */
    public suspend fun prepare(source: CaptureExportSource): PreparedCaptureExport
    /** Writes one already frozen spool without reading mutable live/editor state. */
    public suspend fun write(prepared: PreparedCaptureExport, options: CaptureExportOptions, destination: String,
        progress: (CaptureExportProgress) -> Unit)
    /** Releases only this export's task-owned temporary artifacts. */
    public suspend fun release(prepared: PreparedCaptureExport)
}

/** Prepared semantic HTTP request captured after scripts, before transport-specific encoding. */
public data class StudioExportHttpRequest(
    public val url: String,
    public val method: com.devuloopers.knet.traffic.model.http.HttpMethod,
    public val headers: List<Pair<String, String>>,
    public val body: OutboundRequestBody,
)

/** One execution's response evidence, original bytes and bounded retention owned below presentation. */
public interface StudioExportSession {
    /** Random archive-local execution identity, never a document or device authentication key. */
    public val id: String
    /** Semantic kind captured at execution creation, independent of later draft edits. */
    public val copyKind: com.devuloopers.knet.domain.request.descriptor.RequestKindId get() = com.devuloopers.knet.domain.request.descriptor.RequestKindId.HTTP
    /** Eligibility changes only after actual server-response evidence. */
    public val eligible: StateFlow<Boolean>
    /** True as soon as actual server evidence exists, including an HTTP response still streaming. */
    public val responseReceived: StateFlow<Boolean> get() = eligible
    /** Stores response metadata without inferring success from its status class. */
    public suspend fun response(head: HttpExecutionResponseHead)
    /** Copies one bounded original response segment to private disk storage. */
    public suspend fun append(bytes: ByteArray)
    /** Finalizes measured metadata; fallback executors can provide a terminal response only. */
    public suspend fun complete(result: ExecutionResult)
    /** Retains native protocol events independently of the visible rolling message list. */
    public suspend fun event(event: ApiStudioProtocolExecutionEvent)
    /** Marks retained evidence partial after cancellation/failure; does not erase received data. */
    public fun interrupted()
    /** Retires an execution once its UI owner clears/replaces/disposes it. */
    public fun release()
}

/** Creates per-execution export storage without depending on the user's capture on/off selection. */
public interface StudioExportRecorder {
    /** Begins an HTTP execution with the exact post-script semantic request. */
    public suspend fun http(request: StudioExportHttpRequest): StudioExportSession
    /** Begins one native protocol execution; typed draft data excludes executable scripts. */
    public fun protocol(command: ApiStudioProtocolExecutionCommand, draft: ApiStudioProtocolDraft): StudioExportSession
}
