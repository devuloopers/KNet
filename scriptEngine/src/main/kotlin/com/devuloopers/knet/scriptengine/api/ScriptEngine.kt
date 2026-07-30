package com.devuloopers.knet.scriptengine.api

/**
 * Unified interface for multi-language script execution engines in KNet API Studio.
 * Every language implementation (GraalJS, Kotlin Scripting, etc.) implements this interface.
 */
interface ScriptEngine {

    /**
     * Identifies the [ScriptLanguage] supported by this execution engine.
     */
    val language: ScriptLanguage

    /**
     * Executes the given script source code asynchronously against request/response models and environment.
     *
     * @param code The script source code string to evaluate.
     * @param request The [ScriptRequestModel] representing the HTTP request.
     * @param response Optional [ScriptResponseModel] representing the HTTP response.
     * @param environment The thread-safe [EnvironmentStore] providing variable read/write access.
     * @return A [ScriptExecutionResult] containing assertion results, logs, and updated environment state.
     */
    suspend fun execute(
        code: String,
        request: ScriptRequestModel,
        response: ScriptResponseModel?,
        environment: EnvironmentStore
    ): ScriptExecutionResult
}
