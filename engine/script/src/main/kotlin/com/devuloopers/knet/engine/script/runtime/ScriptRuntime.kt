package com.devuloopers.knet.engine.script.runtime

import com.devuloopers.knet.engine.script.api.EnvironmentStore
import com.devuloopers.knet.engine.script.api.ScriptExecutionResult
import com.devuloopers.knet.engine.script.api.ScriptLanguage
import com.devuloopers.knet.engine.script.api.ScriptRequestModel
import com.devuloopers.knet.engine.script.api.ScriptResponseModel

/**
 * Top-level runtime wrapper delegating execution requests to [ScriptEngineManager].
 */
object ScriptRuntime {
    private val manager = ScriptEngineManager()

    /**
     * Executes a script using the default manager instance.
     */
    suspend fun execute(
        language: ScriptLanguage,
        code: String,
        request: ScriptRequestModel,
        response: ScriptResponseModel?,
        environment: EnvironmentStore,
        timeoutMs: Long = TimeoutExecutor.DEFAULT_TIMEOUT_MS
    ): ScriptExecutionResult {
        return manager.execute(language, code, request, response, environment, timeoutMs)
    }
}
