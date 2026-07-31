package com.devuloopers.knet.scriptengine.kotlin

import com.devuloopers.knet.scriptengine.api.EnvironmentStore
import com.devuloopers.knet.scriptengine.api.ScriptEngine
import com.devuloopers.knet.scriptengine.api.ScriptExecutionResult
import com.devuloopers.knet.scriptengine.api.ScriptLanguage
import com.devuloopers.knet.scriptengine.api.ScriptRequestModel
import com.devuloopers.knet.scriptengine.api.ScriptResponseModel
import com.devuloopers.knet.scriptengine.kotlin.runtime.RuntimeCapabilityDetector

/**
 * Kotlin Scripting Engine powering KNet API Studio.
 * Coordinates execution lifecycle by delegating execution to the active [KotlinRuntime]
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

        return activeRuntime.execute(
            code = code,
            request = request,
            response = response,
            environment = environment
        )
    }
}
