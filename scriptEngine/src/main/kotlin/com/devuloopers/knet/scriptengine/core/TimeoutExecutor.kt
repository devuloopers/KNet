package com.devuloopers.knet.scriptengine.core

import com.devuloopers.knet.scriptengine.api.ScriptExecutionResult
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Timeout execution guard protecting against infinite script loops and execution stalls.
 */
object TimeoutExecutor {

    /** Default max execution timeout limit in milliseconds (2000ms = 2s). */
    const val DEFAULT_TIMEOUT_MS: Long = 2000L

    /**
     * Executes the provided [block] with a maximum timeout constraint.
     *
     * @param timeoutMs Maximum allowed execution time in milliseconds.
     * @param onTimeout Cleanup block to invoke if timeout occurs (e.g. polyglot context forced close).
     * @param block The suspending execution block to evaluate.
     * @return The [ScriptExecutionResult] returned by block, or [ScriptExecutionResult.Error] on timeout.
     */
    suspend fun executeWithTimeout(
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        onTimeout: (() -> Unit)? = null,
        block: suspend () -> ScriptExecutionResult
    ): ScriptExecutionResult {
        return withTimeoutOrNull(timeoutMs) {
            block()
        } ?: run {
            onTimeout?.invoke()
            ScriptExecutionResult.Error(
                message = "Script execution timed out after $timeoutMs ms (Infinite loop or execution stall detected)."
            )
        }
    }
}
