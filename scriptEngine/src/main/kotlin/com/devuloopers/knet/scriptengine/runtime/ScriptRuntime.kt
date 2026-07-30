package com.devuloopers.knet.scriptengine.runtime

import com.devuloopers.knet.scriptengine.api.EnvironmentStore
import com.devuloopers.knet.scriptengine.api.ScriptExecutionResult
import com.devuloopers.knet.scriptengine.api.ScriptLanguage
import com.devuloopers.knet.scriptengine.api.ScriptRequestModel
import com.devuloopers.knet.scriptengine.api.ScriptResponseModel
import com.devuloopers.knet.scriptengine.core.ScriptEngineManager
import com.devuloopers.knet.scriptengine.sandbox.ScriptSanitizer

/**
 * Main runtime execution host managing pre-request scripts and test assertions for KNet API Studio.
 * Routes execution requests to the multi-language [ScriptEngineManager].
 */
class ScriptRuntime {

    private val engineManager = ScriptEngineManager()

    /**
     * Executes the given script source code against request and response models.
     *
     * @param code Script source code string.
     * @param language The target [ScriptLanguage].
     * @param request HTTP request model.
     * @param response Optional HTTP response model.
     * @param environment Initial map of environment variables.
     * @return Execution result [ScriptExecutionResult].
     */
    suspend fun executeScript(
        code: String,
        language: ScriptLanguage,
        request: ScriptRequestModel,
        response: ScriptResponseModel? = null,
        environment: Map<String, String> = emptyMap()
    ): ScriptExecutionResult {
        if (code.isBlank()) {
            return ScriptExecutionResult.Success(
                request = request,
                testResults = emptyList(),
                environmentUpdates = environment,
                logs = emptyList()
            )
        }

        // Pre-execution security sanitization check
        val sanitization = ScriptSanitizer.validate(code)
        if (!sanitization.isValid) {
            return ScriptExecutionResult.Error(
                message = sanitization.errorMessage ?: "Security Violation",
                line = sanitization.line
            )
        }

        val environmentStore = EnvironmentStore(initialValues = environment)

        return engineManager.execute(
            language = language,
            code = code,
            request = request,
            response = response,
            environment = environmentStore
        )
    }
}
