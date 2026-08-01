package com.devuloopers.knet.engine.script.api

/**
 * Supported scripting languages for KNet API Studio.
 */
enum class ScriptLanguage {
    /** JavaScript language engine powered by GraalJS. */
    JAVASCRIPT,
    /** Kotlin Scripting language engine powered by kotlin-scripting-jvm. */
    KOTLIN
}

/**
 * Request model exposed to scripts for inspecting or mutating outgoing HTTP request data.
 *
 * @property url Target HTTP/HTTPS URL string.
 * @property method HTTP method verb (GET, POST, PUT, DELETE, etc.).
 * @property headers Map of request header names and values.
 * @property queryParams Map of request URL query parameters.
 * @property body Request body payload string.
 */
data class ScriptRequestModel(
    var url: String,
    var method: String,
    val headers: MutableMap<String, String>,
    val queryParams: MutableMap<String, String>,
    var body: String
)

/**
 * Read-only response model exposed to test scripts for inspecting received HTTP response data.
 *
 * @property statusCode HTTP status integer code (e.g. 200, 404, 500).
 * @property statusText HTTP status phrase string (e.g. "OK", "Not Found").
 * @property latencyMs Socket response latency in milliseconds.
 * @property responseSizeBytes Response body length in bytes.
 * @property headers Map of response header names and values.
 * @property body Response body payload string.
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
 * Result model capturing individual test assertion outcome.
 *
 * @property name Name or description of the test assertion.
 * @property passed True if assertion succeeded, false if failed.
 * @property errorMessage Optional failure diagnostic message if assertion failed.
 * @property durationMs Execution duration of the test assertion in milliseconds.
 */
data class ScriptTestResult(
    val name: String,
    val passed: Boolean,
    val errorMessage: String? = null,
    val durationMs: Long = 0L
)

/**
 * Sealed hierarchy of unified script execution outcomes.
 */
sealed class ScriptExecutionResult {

    /**
     * Successful script execution output model.
     *
     * @property request The final (potentially mutated) request state.
     * @property testResults List of [ScriptTestResult] instances recorded during execution.
     * @property environmentUpdates Final snapshot of environment variables after script execution.
     * @property logs Captured console log output lines.
     */
    data class Success(
        val request: ScriptRequestModel,
        val testResults: List<ScriptTestResult>,
        val environmentUpdates: Map<String, String>,
        val logs: List<String>
    ) : ScriptExecutionResult()

    /**
     * Error execution result representing syntax, compilation, or runtime failures.
     *
     * @property message Error message description.
     * @property line Line number where error occurred, if available.
     * @property column Column number where error occurred, if available.
     */
    data class Error(
        val message: String,
        val line: Int? = null,
        val column: Int? = null
    ) : ScriptExecutionResult()
}
