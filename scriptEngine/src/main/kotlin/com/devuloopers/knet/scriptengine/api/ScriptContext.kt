package com.devuloopers.knet.scriptengine.api

/**
 * Unified root context object injected into every Kotlin script execution.
 *
 * This is the single entry point for all scripting APIs. Scripts access request
 * and response data exclusively through this object, ensuring a consistent,
 * strongly typed, and extensible API surface.
 *
 * **Canonical scripting API:**
 * ```kotlin
 * test("Status") { context.response.statusCode == 200 }
 * test("Latency") { context.response.latencyMs < 500 }
 * test("Body")   { context.response.body.contains("success") }
 * ```
 *
 * Convenience aliases `request` and `response` are also injected into the script
 * header for backward compatibility, but all documentation uses `context.*`.
 *
 * @property request     The outgoing HTTP request model (may be mutated by pre-request scripts).
 * @property response    The received HTTP response model, or null in pre-request script phase.
 * @property environment Read/write environment variable store for the current execution.
 * @property globals     Reserved: workspace-level global variables (Phase 2 — not yet populated).
 * @property variables   Reserved: collection-level variables (Phase 2 — not yet populated).
 */
data class ScriptContext(
    val request: ScriptRequestModel,
    val response: ScriptResponseModel?,
    val environment: EnvironmentStore,

    // Phase 2 extension slots — stable shape, not yet populated.
    // Using Map<String, String> to avoid introducing undefined types now.
    val globals: Map<String, String> = emptyMap(),
    val variables: Map<String, String> = emptyMap()
)
