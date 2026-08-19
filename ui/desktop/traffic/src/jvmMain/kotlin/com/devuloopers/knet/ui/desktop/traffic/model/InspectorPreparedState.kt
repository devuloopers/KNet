package com.devuloopers.knet.ui.desktop.traffic.model

import com.devuloopers.knet.ui.desktop.httppanel.model.PayloadInspectionSpec
import com.devuloopers.knet.traffic.inspection.InspectionAnnotation
import com.devuloopers.knet.traffic.model.HttpExchangeSnapshot

/** Lifecycle of the canonical detail currently prepared for the Traffic inspector. */
enum class InspectorLoadState {
    /** No exchange is selected. */
    IDLE,

    /** Canonical metadata and bounded body previews are being prepared. */
    LOADING,

    /** Canonical metadata and all available bounded previews are ready. */
    READY,

    /** The selected exchange disappeared before its canonical detail could be loaded. */
    MISSING,
}

/**
 * Immutable inspector state holding fully-resolved body inspection specifications for request and response tabs.
 *
 * [requestPayloadSpec] and [responsePayloadSpec] are computed once off-thread in [TrafficViewModel]
 * via [PayloadInspectionSpec.fromBytes]. Downstream composables ([RequestViewPanel], [ResponseViewPanel])
 * consume these directly and never call [com.devuloopers.knet.engine.formatter.registry.BodyFormatterRegistry] themselves.
 *
 * @property transactionId Unique ID of the currently prepared transaction.
 * @property exchange Canonical exchange whose identity must match [transactionId].
 * @property requestPayloadSpec Fully-resolved request [PayloadInspectionSpec].
 * @property responsePayloadSpec Fully-resolved response [PayloadInspectionSpec].
 * @property requestBodyTruncated Whether the request preview stopped before the stored body end.
 * @property responseBodyTruncated Whether the response preview stopped before the stored body end.
 * @property annotations Protocol-neutral annotations attached to this exact exchange.
 * @property loadState Current canonical-detail preparation lifecycle.
 */
data class InspectorPreparedState(
    val transactionId: String = "",
    val exchange: HttpExchangeSnapshot? = null,
    val requestPayloadSpec: PayloadInspectionSpec = PayloadInspectionSpec.EMPTY,
    val responsePayloadSpec: PayloadInspectionSpec = PayloadInspectionSpec.EMPTY,
    val requestBodyTruncated: Boolean = false,
    val responseBodyTruncated: Boolean = false,
    val annotations: List<InspectionAnnotation> = emptyList(),
    val loadState: InspectorLoadState = InspectorLoadState.IDLE,
) {
    /** True while canonical detail and bounded previews are being prepared. */
    val isPreparing: Boolean
        get() = loadState == InspectorLoadState.LOADING

    /**
     * Decoded request body text. Convenience accessor for callers that only need the raw string.
     */
    val requestBodyText: String get() = requestPayloadSpec.rawBody

    /**
     * Decoded response body text. Convenience accessor for callers that only need the raw string.
     */
    val responseBodyText: String get() = responsePayloadSpec.rawBody

    /** Conservative memory weight used by the bounded inspector-detail cache. */
    val estimatedRetainedBytes: Long
        get() = requestPayloadSpec.estimatedRetainedBytes +
            responsePayloadSpec.estimatedRetainedBytes +
            annotations.sumOf { annotation -> annotation.toString().length.toLong() * 2L } +
            INSPECTOR_METADATA_ESTIMATE_BYTES

    /** Returns this state only when it belongs to [selectedTransactionId]. */
    fun forSelection(selectedTransactionId: String): InspectorPreparedState? =
        takeIf { transactionId == selectedTransactionId }

    companion object {
        /** Creates the identity-bearing loading state for a newly selected exchange. */
        fun loading(transactionId: String): InspectorPreparedState = InspectorPreparedState(
            transactionId = transactionId,
            loadState = InspectorLoadState.LOADING,
        )

        private const val INSPECTOR_METADATA_ESTIMATE_BYTES = 1_024L
    }
}
