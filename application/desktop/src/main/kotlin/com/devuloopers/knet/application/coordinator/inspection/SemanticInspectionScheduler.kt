package com.devuloopers.knet.application.coordinator.inspection

import com.devuloopers.knet.application.contract.inspection.InspectionAnnotationStore
import com.devuloopers.knet.application.contract.inspection.InspectionBody
import com.devuloopers.knet.application.contract.inspection.SemanticInspectionInput
import com.devuloopers.knet.application.contract.inspection.SemanticInspectionPolicy
import com.devuloopers.knet.application.contract.inspection.SemanticInspector
import com.devuloopers.knet.application.contract.traffic.BodyChunk
import com.devuloopers.knet.application.contract.traffic.BodyRange
import com.devuloopers.knet.application.contract.traffic.TrafficQuery
import com.devuloopers.knet.traffic.id.CaptureSessionId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.inspection.InspectionAnnotation
import com.devuloopers.knet.traffic.inspection.InspectionAnnotationState
import com.devuloopers.knet.traffic.model.body.MessageBodyRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds

/**
 * Budgeted application scheduler. Callers invoke it only after capture publication; inspector
 * failure, timeout, or annotation-store failure never participates in proxy forwarding.
 */
public class SemanticInspectionScheduler(
    private val trafficQuery: TrafficQuery,
    private val annotations: InspectionAnnotationStore,
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
            withTimeout(policy.inspectorTimeoutMillis.milliseconds) { inspector.inspect(input) }
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
