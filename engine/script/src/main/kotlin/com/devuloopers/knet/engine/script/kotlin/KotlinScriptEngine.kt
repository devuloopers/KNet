package com.devuloopers.knet.engine.script.kotlin

import com.devuloopers.knet.engine.script.api.EnvironmentStore
import com.devuloopers.knet.engine.script.api.ScriptEngine
import com.devuloopers.knet.engine.script.api.ScriptExecutionResult
import com.devuloopers.knet.scripting.model.ScriptLanguage
import com.devuloopers.knet.engine.script.api.ScriptRequestModel
import com.devuloopers.knet.engine.script.api.ScriptResponseModel
import com.devuloopers.knet.engine.script.internal.RuntimeCapabilityDetector

/**
 * Kotlin Scripting Engine powering KNet API Studio.
 * Coordinates execution lifecycle by delegating execution to the active [com.devuloopers.knet.engine.script.kotlin.runtime.KotlinRuntime]
 * resolved via [RuntimeCapabilityDetector].
 */
class KotlinScriptEngine(
    detector: RuntimeCapabilityDetector = RuntimeCapabilityDetector()
) : ScriptEngine {

    override val language: ScriptLanguage = ScriptLanguage.KOTLIN

    private val activeRuntime = detector.selectRuntime()

    /**
     * Executes Kotlin scripts against request, response, and environment variables.
     *
     * @param code The Kotlin script code string to evaluate.
     * @param request The [ScriptRequestModel] representing the HTTP request.
     * @param response Optional [ScriptResponseModel] representing the HTTP response.
     * @param environment Thread-safe [EnvironmentStore] for reading and updating variables.
     * @return Result model [ScriptExecutionResult].
     */
    override suspend fun execute(
        code: String,
        request: ScriptRequestModel,
        response: ScriptResponseModel?,
        environment: EnvironmentStore
    ): ScriptExecutionResult {
        if (code.isBlank()) {
            return ScriptExecutionResult.Success(
                request = request,
                testResults = emptyList(),
                environmentUpdates = environment.snapshot(),
                logs = emptyList()
            )
        }

        return activeRuntime.execute(code, request, response, environment)
    }
}
