package com.devuloopers.knet.application.contract.script

import com.devuloopers.knet.scripting.model.ScriptAssertion
import com.devuloopers.knet.scripting.model.ScriptLanguage

/** Immutable request value passed to the bounded script runtime adapter. */
public data class ScriptRequest(
    public val url: String,
    public val method: String,
    public val headers: Map<String, String>,
    public val queryParameters: Map<String, String>,
    public val body: String,
)

/** Immutable response value available to post-response scripts. */
public data class ScriptResponse(
    public val statusCode: Int,
    public val statusText: String,
    public val latencyMillis: Long,
    public val responseSizeBytes: Long,
    public val headers: Map<String, String>,
    public val body: String,
)

/** One isolated script invocation with an explicit environment snapshot. */
public data class ScriptExecutionCommand(
    public val language: ScriptLanguage,
    public val source: String,
    public val request: ScriptRequest,
    public val response: ScriptResponse?,
    public val environment: Map<String, String> = emptyMap(),
)

/** Technology-neutral result returned by [ScriptExecutor]. */
public sealed interface ScriptExecutionOutcome {
    /** Successful execution, including request mutations and the next environment snapshot. */
    public data class Success(
        public val request: ScriptRequest,
        public val assertions: List<ScriptAssertion>,
        public val environment: Map<String, String>,
        public val logs: List<String>,
    ) : ScriptExecutionOutcome

    /** Bounded compilation or runtime failure safe to present to a feature. */
    public data class Failure(public val message: String) : ScriptExecutionOutcome
}

/** Application boundary for sandboxed, deadline-limited script execution. */
public fun interface ScriptExecutor {
    public suspend fun execute(command: ScriptExecutionCommand): ScriptExecutionOutcome
}

/** Fail-closed test/default adapter used when scripting was not composed into a feature. */
public object UnavailableScriptExecutor : ScriptExecutor {
    override suspend fun execute(command: ScriptExecutionCommand): ScriptExecutionOutcome =
        ScriptExecutionOutcome.Failure("Script execution is unavailable in this runtime.")
}
