package com.devuloopers.knet.application.port.inspection

import com.devuloopers.knet.application.port.traffic.BodyChunk
import com.devuloopers.knet.application.port.traffic.BodyRange
import com.devuloopers.knet.application.port.traffic.TrafficQueryPort
import com.devuloopers.knet.traffic.id.CaptureSessionId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.inspection.InspectionAnnotation
import com.devuloopers.knet.traffic.inspection.InspectionAnnotationState
import com.devuloopers.knet.traffic.inspection.InspectionDocument
import com.devuloopers.knet.traffic.inspection.InspectorId
import com.devuloopers.knet.traffic.model.HttpExchangeSnapshot
import com.devuloopers.knet.traffic.model.body.MessageBodyRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout

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
public interface InspectionAnnotationPort {
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

/**
 * Budgeted application scheduler. Callers invoke it only after capture publication; inspector
 * failure, timeout, or annotation-store failure never participates in proxy forwarding.
 */
public class SemanticInspectionScheduler(
    private val trafficQuery: TrafficQueryPort,
    private val annotations: InspectionAnnotationPort,
    inspectors: List<SemanticInspector>,
    private val policy: SemanticInspectionPolicy = SemanticInspectionPolicy(),
) {
    private val inspectors = inspectors.sortedByDescending(SemanticInspector::priority)
    private val permits = Semaphore(policy.maximumConcurrentInspections)

    /** Runs all matching inspectors for one canonical exchange under bounded body/time budgets. */
    public suspend fun inspect(
        sessionId: CaptureSessionId,
        exchangeId: ExchangeId,
        createdAtEpochMillis: Long,
    ): List<InspectionAnnotation> = permits.withPermit {
        val exchange = trafficQuery.getExchange(exchangeId) ?: return@withPermit emptyList()
        val matching = inspectors.filter { it.supports(exchange) }
        if (matching.isEmpty()) return@withPermit emptyList()
        val bodyLimit = minOf(
            policy.maximumBodyBytes,
            matching.maxOf(SemanticInspector::bodyBudgetBytes),
        )
        val input = SemanticInspectionInput(
            exchange = exchange,
            requestBody = readBody(exchange.request.body, bodyLimit),
            responseBody = exchange.response?.let { readBody(it.body, bodyLimit) },
        )
        matching.map { inspector ->
            val annotation = inspectOne(inspector, input, createdAtEpochMillis)
            try {
                annotations.put(sessionId, annotation)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Annotation persistence is deliberately isolated from other inspectors.
            }
            annotation
        }
    }

    private suspend fun inspectOne(
        inspector: SemanticInspector,
        input: SemanticInspectionInput,
        createdAtEpochMillis: Long,
    ): InspectionAnnotation {
        var errorCode: String? = null
        val document = try {
            withTimeout(policy.inspectorTimeoutMillis) { inspector.inspect(input) }
        } catch (_: TimeoutCancellationException) {
            errorCode = "inspector_timeout"
            null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            errorCode = "inspector_failed"
            null
        }
        val state = when {
            errorCode != null -> InspectionAnnotationState.FAILED
            document == null -> InspectionAnnotationState.SKIPPED
            else -> InspectionAnnotationState.COMPLETED
        }
        return InspectionAnnotation(
            exchangeId = input.exchange.id,
            inspectorId = inspector.id,
            schemaVersion = inspector.schemaVersion,
            state = state,
            document = document,
            errorCode = errorCode,
            createdAtEpochMillis = createdAtEpochMillis,
        )
    }

    private suspend fun readBody(reference: MessageBodyRef, limit: Int): InspectionBody? {
        val body = (reference as? MessageBodyRef.Available)?.body ?: return null
        if (body.storedBytes == 0L) return null
        val chunks = mutableListOf<BodyChunk>()
        var offset = 0L
        while (offset < body.storedBytes && offset < limit) {
            val length = minOf(BODY_RANGE_BYTES.toLong(), body.storedBytes - offset, limit.toLong() - offset).toInt()
            if (length <= 0) break
            val chunk = try {
                trafficQuery.readBody(body.id, BodyRange(offset, length))
            } catch (_: IllegalStateException) {
                return null
            }
            chunks += chunk
            offset += chunk.size
            if (chunk.endOfBody || chunk.size == 0) break
        }
        return InspectionBody(
            chunks = chunks,
            truncated = offset < body.storedBytes,
        )
    }

    private companion object {
        private const val BODY_RANGE_BYTES: Int = 1_048_576
    }
}

/** Reads durable generic annotations for one Traffic detail selection. */
public class ObserveInspectionAnnotationsUseCase(
    private val annotations: InspectionAnnotationPort,
) {
    public fun execute(exchangeId: ExchangeId): Flow<List<InspectionAnnotation>> = annotations.observe(exchangeId)

    /** Observes semantic annotations for one bounded Traffic row window. */
    public fun execute(
        exchangeIds: Set<ExchangeId>,
    ): Flow<Map<ExchangeId, List<InspectionAnnotation>>> = annotations.observe(exchangeIds)
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
