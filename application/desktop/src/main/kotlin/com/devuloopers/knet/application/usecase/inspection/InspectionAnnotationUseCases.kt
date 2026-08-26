package com.devuloopers.knet.application.usecase.inspection

import com.devuloopers.knet.application.contract.inspection.InspectionAnnotationStore
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.inspection.InspectionAnnotation
import kotlinx.coroutines.flow.Flow

/** Reads durable generic annotations for one Traffic detail selection. */
public class ObserveInspectionAnnotationsUseCase(
    private val annotations: InspectionAnnotationStore,
) {
    public fun execute(exchangeId: ExchangeId): Flow<List<InspectionAnnotation>> = annotations.observe(exchangeId)

    /** Observes semantic annotations for one bounded Traffic row window. */
    public fun execute(
        exchangeIds: Set<ExchangeId>,
    ): Flow<Map<ExchangeId, List<InspectionAnnotation>>> = annotations.observe(exchangeIds)
}
