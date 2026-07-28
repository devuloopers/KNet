package com.devuloopers.knet.scriptengine.api

/**
 * Supported scripting languages for KNet API Studio.
 */
enum class ScriptLanguage {
    JAVASCRIPT,
    KOTLIN
}

/**
 * Read/Write Request model exposed to scripts.
 */
data class ScriptRequestModel(
    var url: String,
    var method: String,
    val headers: MutableMap<String, String>,
    val queryParams: MutableMap<String, String>,
    var body: String
)

/**
 * Read-only Response model exposed to test scripts.
 */
data class ScriptResponseModel(
    val statusCode: Int,
    val statusText: String,
    val latencyMs: Long,
    val responseSizeBytes: Long,
    val headers: Map<String, String>,
    val body: String
)

/**
 * Result model for individual test assertions.
 */
data class ScriptTestResult(
    val name: String,
    val passed: Boolean,
    val errorMessage: String? = null,
    val durationMs: Long = 0L
)

/**
 * Unified execution result returned by [ScriptRuntime].
 */
sealed class ScriptExecutionResult {
    data class Success(
        val request: ScriptRequestModel,
        val testResults: List<ScriptTestResult>,
        val environmentUpdates: Map<String, String>,
        val logs: List<String>
    ) : ScriptExecutionResult()

    data class Error(
        val message: String,
        val line: Int? = null,
        val column: Int? = null
    ) : ScriptExecutionResult()
}
