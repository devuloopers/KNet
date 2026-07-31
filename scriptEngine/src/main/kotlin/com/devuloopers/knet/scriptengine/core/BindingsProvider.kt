package com.devuloopers.knet.scriptengine.core

import com.devuloopers.knet.scriptengine.api.EnvironmentStore
import com.devuloopers.knet.scriptengine.api.ScriptContext
import com.devuloopers.knet.scriptengine.api.ScriptRequestModel
import com.devuloopers.knet.scriptengine.api.ScriptResponseModel

/**
 * Shared provider exposing script execution bindings (request, response, environment, assertions, logging)
 * to multi-language script runtimes (Kotlin, JavaScript).
 *
 * The primary entry point for Kotlin scripts is the strongly typed [ScriptContext] bound under the
 * key `"context"`. Convenience aliases (`request`, `response`) are retained as separate keys so the
 * generated script header can perform a typed self-cast without relying on internal engine APIs.
 */
class BindingsProvider(
    val request: ScriptRequestModel,
    val response: ScriptResponseModel?,
    val environment: EnvironmentStore,
    val resultCollector: ResultCollector
) {
    /**
     * Maps bindings to key-value pairs suitable for JSR-223 engine bindings or dynamic script context injection.
     *
     * Key order and naming is part of the public scripting contract:
     * - `context`         — Strongly typed [ScriptContext] root object (canonical API).
     * - `request`         — [ScriptRequestModel] alias retained for header self-cast and backward compat.
     * - `response`        — [ScriptResponseModel] alias retained for header self-cast and backward compat.
     * - `environment`/`env` — [EnvironmentStore] for reading and writing env variables.
     * - `resultCollector` — Internal [ResultCollector] used by the `test()` DSL helper.
     */
    fun createBindingsMap(): Map<String, Any?> {
        return mapOf(
            "context"         to ScriptContext(request, response, environment),
            "request"         to request,
            "response"        to response,
            "environment"     to environment,
            "env"             to environment,
            "resultCollector" to resultCollector
        )
    }
}
