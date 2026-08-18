package com.devuloopers.knet.ui.desktop.traffic.model

import com.devuloopers.knet.ui.desktop.httppanel.model.PayloadInspectionSpec
import com.devuloopers.knet.traffic.inspection.InspectionAnnotation

/**
 * Immutable inspector state holding fully-resolved body inspection specifications for request and response tabs.
 *
 * [requestPayloadSpec] and [responsePayloadSpec] are computed once off-thread in [TrafficViewModel]
 * via [PayloadInspectionSpec.fromBytes]. Downstream composables ([RequestViewPanel], [ResponseViewPanel])
 * consume these directly and never call [com.devuloopers.knet.engine.formatter.registry.BodyFormatterRegistry] themselves.
 *
 * @property transactionId Unique ID of the currently prepared transaction.
 * @property requestBodySpec Fully-resolved request [PayloadInspectionSpec] (decoded text, headers, and BodyFormat).
 * @property responseBodySpec Fully-resolved response [PayloadInspectionSpec] (decoded text, headers, and BodyFormat).
 * @property requestBodyTruncated Whether the request preview stopped before the stored body end.
 * @property responseBodyTruncated Whether the response preview stopped before the stored body end.
 * @property isPreparing True if the body payloads are actively being loaded or resolved off-thread.
 */
data class InspectorPreparedState(
    val transactionId: String = "",
    val requestPayloadSpec: PayloadInspectionSpec = PayloadInspectionSpec.EMPTY,
    val responsePayloadSpec: PayloadInspectionSpec = PayloadInspectionSpec.EMPTY,
    val requestBodyTruncated: Boolean = false,
    val responseBodyTruncated: Boolean = false,
    val annotations: List<InspectionAnnotation> = emptyList(),
    val isPreparing: Boolean = false
) {
    /**
     * Decoded request body text. Convenience accessor for callers that only need the raw string.
     */
    val requestBodyText: String get() = requestPayloadSpec.rawBody

    /**
     * Decoded response body text. Convenience accessor for callers that only need the raw string.
     */
    val responseBodyText: String get() = responsePayloadSpec.rawBody
}
