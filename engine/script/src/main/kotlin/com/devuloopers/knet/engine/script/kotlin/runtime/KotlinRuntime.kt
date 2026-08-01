package com.devuloopers.knet.engine.script.kotlin.runtime

import com.devuloopers.knet.engine.script.api.EnvironmentStore
import com.devuloopers.knet.engine.script.api.ScriptExecutionResult
import com.devuloopers.knet.engine.script.api.ScriptRequestModel
import com.devuloopers.knet.engine.script.api.ScriptResponseModel

/**
 * Execution runtime strategy for evaluating Kotlin scripts.
 */
interface KotlinRuntime {
    /**
     * Returns true if this runtime strategy is operational in the current JVM environment.
     */
    fun isAvailable(): Boolean

    /**
     * Executes Kotlin script code asynchronously against request, response, and environment.
     */
    suspend fun execute(
        code: String,
        request: ScriptRequestModel,
        response: ScriptResponseModel?,
        environment: EnvironmentStore
    ): ScriptExecutionResult
}
