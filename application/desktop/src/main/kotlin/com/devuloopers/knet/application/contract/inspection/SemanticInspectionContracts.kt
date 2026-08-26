package com.devuloopers.knet.application.contract.inspection

import com.devuloopers.knet.application.contract.traffic.BodyChunk
import com.devuloopers.knet.traffic.id.CaptureSessionId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.inspection.InspectionAnnotation
import com.devuloopers.knet.traffic.inspection.InspectionDocument
import com.devuloopers.knet.traffic.inspection.InspectorId
import com.devuloopers.knet.traffic.model.HttpExchangeSnapshot
import kotlinx.coroutines.flow.Flow

/** Bounded body content made available to an asynchronous semantic inspector. */
public data class InspectionBody(
    public val chunks: List<BodyChunk>,
    public val truncated: Boolean,
) {
    /** Total retained bytes across immutable chunks. */
    public val size: Int
        get() = chunks.sumOf(BodyChunk::size)
}

/** Protocol-neutral input assembled outside transport/capture paths. */
public data class SemanticInspectionInput(
    public val exchange: HttpExchangeSnapshot,
    public val requestBody: InspectionBody?,
    public val responseBody: InspectionBody?,
)

/** Additive semantic inspector extension. */
public interface SemanticInspector {
    public val id: InspectorId
    public val schemaVersion: Long
    public val priority: Int
    public val bodyBudgetBytes: Int

    /** Cheap metadata-only predicate evaluated before any body is loaded. */
    public fun supports(exchange: HttpExchangeSnapshot): Boolean

    /** Produces a generic versioned document, or null when content does not match after parsing. */
    public suspend fun inspect(input: SemanticInspectionInput): InspectionDocument?
}

/** Persistence/query boundary for versioned semantic annotations. */
public interface InspectionAnnotationStore {
    public suspend fun put(sessionId: CaptureSessionId, annotation: InspectionAnnotation)
    public suspend fun get(exchangeId: ExchangeId): List<InspectionAnnotation>
    public fun observe(exchangeId: ExchangeId): Flow<List<InspectionAnnotation>>

    /**
     * Observes annotations for a bounded set of exchanges as one storage query.
     *
     * Exchanges without annotations are omitted from the returned map. Callers must keep [exchangeIds]
     * bounded to their visible or retained presentation window.
     */
    public fun observe(exchangeIds: Set<ExchangeId>): Flow<Map<ExchangeId, List<InspectionAnnotation>>>
}

/** Independent scheduler budgets; no value can make forwarding wait for inspection. */
public data class SemanticInspectionPolicy(
    public val maximumConcurrentInspections: Int = 4,
    public val maximumBodyBytes: Int = 1_048_576,
    public val inspectorTimeoutMillis: Long = 2_000L,
) {
    init {
        require(maximumConcurrentInspections in 1..64) { "Inspection concurrency limit is invalid." }
        require(maximumBodyBytes in 1..(16 * 1024 * 1024)) { "Inspection body limit is invalid." }
        require(inspectorTimeoutMillis in 10L..60_000L) { "Inspection timeout is invalid." }
    }
}

/** Capability claim maturity displayed by KNet. */
public enum class CapabilityMaturity {
    SUPPORTED,
    EXPERIMENTAL,
    UNAVAILABLE,
}

/** Evidence-backed runtime protocol/inspection capability. */
public data class RuntimeCapability(
    public val id: String,
    public val displayName: String,
    public val maturity: CapabilityMaturity,
    public val evidence: String? = null,
) {
    init {
        require(id.isNotBlank() && displayName.isNotBlank()) { "Capability identity must not be blank." }
        require(maturity != CapabilityMaturity.SUPPORTED || !evidence.isNullOrBlank()) {
            "A supported capability requires test evidence."
        }
    }
}

/** Immutable capability catalog. Additive transports/inspectors contribute new entries. */
public class RuntimeCapabilityCatalog(capabilities: List<RuntimeCapability>) {
    public val capabilities: List<RuntimeCapability> = capabilities.distinctBy(RuntimeCapability::id)

    public fun get(id: String): RuntimeCapability? = capabilities.firstOrNull { it.id == id }
}
