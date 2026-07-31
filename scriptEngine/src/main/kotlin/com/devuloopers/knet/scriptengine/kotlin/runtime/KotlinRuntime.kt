package com.devuloopers.knet.scriptengine.kotlin.runtime

import com.devuloopers.knet.scriptengine.api.EnvironmentStore
import com.devuloopers.knet.scriptengine.api.ScriptExecutionResult
import com.devuloopers.knet.scriptengine.api.ScriptRequestModel
import com.devuloopers.knet.scriptengine.api.ScriptResponseModel

/**
 * Abstraction interface for Kotlin script execution runtimes.
 * Allows interchangeable execution strategies (Native JSR-223, ExpressionRuntime, K2 Host API).
 */
interface KotlinRuntime {

    /**
     * Executes Kotlin script code against the provided request, response, and environment store.
     *
     * @param code Kotlin script source string.
     * @param request HTTP request model.
     * @param response Optional HTTP response model.
     * @param environment Environment variables store.
     * @return [ScriptExecutionResult] containing test assertion results, environment updates, and logs.
     */
    suspend fun execute(
        code: String,
        request: ScriptRequestModel,
        response: ScriptResponseModel?,
        environment: EnvironmentStore
    ): ScriptExecutionResult

    /**
     * Reports whether this runtime strategy is available and operational on the current platform/classpath.
     */
    fun isAvailable(): Boolean
}
