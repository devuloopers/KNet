package com.devuloopers.knet.engine.script.api

/**
 * Unified root context object injected into script execution.
 *
 * This is the single entry point for all scripting APIs. Scripts access request
 * and response data through this object, ensuring a consistent, strongly typed API surface.
 *
 * @property request The outgoing HTTP request model.
 * @property response The received HTTP response model, or null in pre-request script phase.
 * @property environment Read/write environment variable store for the current execution.
 * @property globals Workspace-level global variables.
 * @property variables Collection-level variables.
 */
data class ScriptContext(
    val request: ScriptRequestModel,
    val response: ScriptResponseModel?,
    val environment: EnvironmentStore,
    val globals: Map<String, String> = emptyMap(),
    val variables: Map<String, String> = emptyMap()
)
