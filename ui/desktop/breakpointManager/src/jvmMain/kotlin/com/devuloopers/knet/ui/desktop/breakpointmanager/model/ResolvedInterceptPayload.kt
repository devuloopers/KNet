package com.devuloopers.knet.ui.desktop.breakpointmanager.model

import com.devuloopers.knet.ui.desktop.httppanel.model.PayloadInspectionSpec

/**
 * Immutable container holding pre-resolved body inspection specifications for a single intercepted transaction.
 *
 * Computed off-thread only for the active pending breakpoint and discarded when selection changes.
 *
 * Stored as the bounded active entry in [BreakpointManagerState.resolvedPayloads] so that
 * [LiveInterceptDrawer] can look up the result and construct [RequestBodyState] / [ResponseBodyState]
 * via their respective [fromResolved] factories — without calling
 * [com.devuloopers.knet.engine.formatter.registry.BodyFormatterRegistry] at render time.
 *
 * @property transactionId Unique identifier matching the parent [com.devuloopers.knet.application.port.breakpoint.PendingBreakpoint.id].
 * @property requestBodySpec Fully-resolved request [PayloadInspectionSpec] (decoded text, headers, and BodyFormat).
 * @property responseBodySpec Fully-resolved response [PayloadInspectionSpec], or [PayloadInspectionSpec.EMPTY]
 *   if the transaction is in the request phase and has no response yet.
 */
data class ResolvedInterceptPayload(
    val transactionId: String,
    val requestPayloadSpec: PayloadInspectionSpec,
    val responsePayloadSpec: PayloadInspectionSpec
)
