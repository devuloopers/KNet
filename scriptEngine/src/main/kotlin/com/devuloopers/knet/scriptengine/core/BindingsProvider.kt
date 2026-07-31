package com.devuloopers.knet.scriptengine.core

import com.devuloopers.knet.scriptengine.api.EnvironmentStore
import com.devuloopers.knet.scriptengine.api.ScriptRequestModel
import com.devuloopers.knet.scriptengine.api.ScriptResponseModel

/**
 * Shared provider exposing script execution bindings (request, response, environment, assertions, logging)
 * to multi-language script runtimes (Kotlin, JavaScript).
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
            "request" to request,
            "response" to response,
            "environment" to environment,
            "env" to environment,
            "resultCollector" to resultCollector
        )
    }
}
