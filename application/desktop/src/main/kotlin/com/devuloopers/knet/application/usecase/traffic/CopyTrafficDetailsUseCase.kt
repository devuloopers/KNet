package com.devuloopers.knet.application.usecase.traffic

import com.devuloopers.knet.application.contract.export.CaptureExportSource
import com.devuloopers.knet.application.contract.inspection.GraphQLInspectionMetadata
import com.devuloopers.knet.application.contract.inspection.InspectionAnnotationStore
import com.devuloopers.knet.application.contract.pairing.RegisteredDeviceStore
import com.devuloopers.knet.application.contract.traffic.*
import com.devuloopers.knet.domain.graphql.GraphQLHttpSource
import com.devuloopers.knet.traffic.inspection.InspectionAnnotationState
import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.traffic.model.body.*
import com.devuloopers.knet.traffic.model.http.*
import kotlinx.coroutines.*
import kotlin.time.Instant
import com.devuloopers.knet.application.usecase.traffic.TrafficCopyText as Text
import com.devuloopers.knet.application.usecase.traffic.TrafficCopyRecord as Record
import com.devuloopers.knet.application.usecase.traffic.TrafficCopySupport.safe
import com.devuloopers.knet.application.usecase.traffic.TrafficCopySupport.label
import com.devuloopers.knet.application.usecase.traffic.TrafficCopySupport.observed
import com.devuloopers.knet.application.usecase.traffic.TrafficCopySupport.size
import com.devuloopers.knet.application.usecase.traffic.TrafficCopySupport.host
import com.devuloopers.knet.application.usecase.traffic.TrafficCopySupport.parameters
import com.devuloopers.knet.application.usecase.traffic.TrafficCopySupport.pairs

/** Application command boundary used by clipboard presentation and deterministic lifecycle tests. */
public fun interface PrepareTrafficDetailsUseCase {
    /** Resolves only [source]'s frozen identities into a bounded readable text document. */
    public suspend fun execute(source: CaptureExportSource.Selection, preset: TrafficCopyPreset): String
    /** Backward-compatible full-details command for existing callers. */
    public suspend fun execute(source: CaptureExportSource.Selection): String = execute(source, TrafficCopyPreset.FULL)
}

/**
 * Prepares readable clipboard text from exact canonical identities, independent of the loaded UI page.
 * Source validation precedes body access. A body lease survives concurrent Clear; missing metadata is explicit.
 * JSON/protocol decoding and all storage work run off Main. No files or clipboard APIs enter presentation state.
 */
public class CopyTrafficDetailsUseCase(
    private val traffic: TrafficQuery,
    private val bodies: BodyStore,
    private val messages: ProtocolMessageQuery,
    private val devices: RegisteredDeviceStore,
    private val annotations: InspectionAnnotationStore,
    private val formatting: TrafficDetailsFormatting,
    private val presentations: ProtocolMessagePresentationRegistry,
) : PrepareTrafficDetailsUseCase {
    /**
     * Returns one labelled text document, newest first in canonical Traffic tie order.
     * @param source Frozen, distinct typed record identities and authenticated workspace restriction.
     * @throws IllegalArgumentException If records cross source boundaries or the total clipboard budget is exceeded.
     * @throws IllegalStateException If none of the requested records remain available.
     */
    public override suspend fun execute(source: CaptureExportSource.Selection, preset: TrafficCopyPreset): String = withContext(Dispatchers.IO) {
        val identities = source.records.toList()
        val lease = bodies.retainExportReads()
        try {
            var metadataWeight = 0L
            val records = identities.map { id ->
                currentCoroutineContext().ensureActive()
                val exchange = (id as? TrafficRecordId.Exchange)?.let { traffic.getExchange(it.id) }
                val opaque = (id as? TrafficRecordId.Opaque)?.let { traffic.getOpaqueFlow(it.id) }
                val connection = exchange?.connectionId ?: opaque?.connectionId
                val ingress = connection?.let { traffic.getConnectionIngress(it) }
                if (exchange != null || opaque != null) require(source.source == TrafficSource.All || TrafficSource.fromIngress(ingress?.ingress) == source.source) {
                    "Selected traffic belongs to another device. Select the items again."
                }
                val record = Record(id, exchange, opaque, ingress)
                metadataWeight += record.weight()
                require(metadataWeight <= MAXIMUM_TEXT_CHARACTERS) { TOO_LARGE }
                record
            }.sortedWith(compareByDescending<Record> { it.started }.thenByDescending { if (it.id is TrafficRecordId.Exchange) 0 else 1 }.thenByDescending { it.key })
            check(records.any { it.exchange != null || it.opaque != null }) { "The selected traffic was cleared before it could be copied." }
            val deviceNames = mutableMapOf<TrafficSource, String>()
            suspend fun name(source: TrafficSource): String = deviceNames.getOrPut(source) {
                when (source) {
                    is TrafficSource.Device -> devices.getRegisteredDevice(source.id)?.displayName ?: "Registered device ${source.id.value}"
                    TrafficSource.Other -> "Other traffic"
                    TrafficSource.All -> "All traffic sources"
                }
            }
            TrafficCopyRenderer(bodies, formatting,
                { messages.queryMessages(ProtocolMessagePageQuery(it, limit = TrafficCopyLimits.MESSAGES)) },
                annotations::get, presentations).render(records, preset, name(source.source), deviceSource = source.source is TrafficSource.Device,
                    sourceForRecord = if (source.source == TrafficSource.All) ({ record -> name(TrafficSource.fromIngress(record.ingress?.ingress)) }) else null)
        } finally { lease.close() }
    }

    private companion object {
        const val MAXIMUM_TEXT_CHARACTERS = TrafficCopyLimits.TEXT
        const val TOO_LARGE = TrafficCopyLimits.TOO_LARGE
    }
}
