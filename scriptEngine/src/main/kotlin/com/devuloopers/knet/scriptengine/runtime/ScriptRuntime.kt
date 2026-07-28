package com.devuloopers.knet.scriptengine.runtime

import com.devuloopers.knet.scriptengine.api.ScriptExecutionResult
import com.devuloopers.knet.scriptengine.api.ScriptLanguage
import com.devuloopers.knet.scriptengine.api.ScriptRequestModel
import com.devuloopers.knet.scriptengine.api.ScriptResponseModel
import com.devuloopers.knet.scriptengine.javascript.JsScriptEngine
import com.devuloopers.knet.scriptengine.kotlin.KotlinScriptEngine
import com.devuloopers.knet.scriptengine.sandbox.ScriptSanitizer

/**
 * Main runtime execution host managing pre-request scripts and test assertions.
 */
class ScriptRuntime {

    private val jsEngine = JsScriptEngine()
    private val kotlinEngine = KotlinScriptEngine()

    fun executeScript(
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

        // Pre-execution security check
        val sanitization = ScriptSanitizer.validate(code)
        if (!sanitization.isValid) {
            return ScriptExecutionResult.Error(
                message = sanitization.errorMessage ?: "Security Violation",
                line = sanitization.line
            )
        }

        val envCopy = environment.toMutableMap()

        return when (language) {
            ScriptLanguage.JAVASCRIPT -> jsEngine.execute(code, request, response, envCopy)
            ScriptLanguage.KOTLIN -> kotlinEngine.execute(code, request, response, envCopy)
        }
    }
}
