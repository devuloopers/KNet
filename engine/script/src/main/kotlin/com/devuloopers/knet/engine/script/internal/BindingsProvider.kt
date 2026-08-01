package com.devuloopers.knet.engine.script.internal

import com.devuloopers.knet.engine.script.api.EnvironmentStore
import com.devuloopers.knet.engine.script.api.ScriptContext
import com.devuloopers.knet.engine.script.api.ScriptRequestModel
import com.devuloopers.knet.engine.script.api.ScriptResponseModel

/**
 * Shared provider exposing script execution bindings to multi-language script runtimes.
 */
class BindingsProvider(
    val request: ScriptRequestModel,
    val response: ScriptResponseModel?,
    val environment: EnvironmentStore,
    val resultCollector: ResultCollector
) {
    /**
     * Maps bindings to key-value pairs suitable for JSR-223 engine bindings or dynamic script context injection.
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
