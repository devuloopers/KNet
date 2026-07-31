package com.devuloopers.knet.domain.apistudio.model

import com.devuloopers.knet.domain.apistudio.usecase.ExecutionResult
import com.devuloopers.knet.scriptengine.api.EnvironmentStore

/**
 * Execution-scoped runtime context managing state for a single request execution lifecycle.
 *
 * Owns ephemeral execution state across the Pre-request script phase, HTTP network dispatch,
 * and post-response test assertion phase.
 *
 * @property request Immutable request definition model.
 * @property environmentStore Thread-safe store for environment variables mutated during execution.
 * @property startedAt System timestamp in milliseconds when execution commenced.
 * @property response Live network execution outcome, or null if not yet executed.
 */
data class ExecutionContext(
    val request: SavedApiRequest,
    val environmentStore: EnvironmentStore = EnvironmentStore(),
    val startedAt: Long = System.currentTimeMillis(),
    var response: ExecutionResult? = null
)
