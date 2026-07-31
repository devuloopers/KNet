package com.devuloopers.knet.scriptengine.core

import com.devuloopers.knet.scriptengine.api.ScriptExecutionResult
import org.graalvm.polyglot.PolyglotException

/**
 * Standardized exception formatter converting engine-specific exceptions into uniform error result models.
 *
 * Handles exceptions from all supported scripting engines:
 * - [PolyglotException] — GraalVM JavaScript (GraalJS) engine errors.
 * - [javax.script.ScriptException] — Kotlin JSR-223 compilation and runtime errors.
 * - [Throwable] — Generic fallback for unexpected engine failures.
 */
object ExceptionFormatter {

    /**
     * Formats a raw exception into a structured [ScriptExecutionResult.Error].
     *
     * Produces human-readable error messages with accurate line and column numbers
     * where available, rather than raw JVM stack traces.
     *
     * @param throwable The caught exception instance.
     * @return Formatted [ScriptExecutionResult.Error] object.
     */
    fun format(throwable: Throwable): ScriptExecutionResult.Error {
        return when (throwable) {
            is PolyglotException -> {
                val location = throwable.sourceLocation
                val line = location?.startLine
                val column = location?.startColumn
                ScriptExecutionResult.Error(
                    message = "JavaScript Error: ${throwable.message ?: "Script execution failed"}",
                    line = line,
                    column = column
                )
            }
            is javax.script.ScriptException -> {
                // Kotlin JSR-223 compilation and runtime errors.
                // lineNumber / columnNumber return -1 when unavailable; filter those out.
                ScriptExecutionResult.Error(
                    message = "Script Error: ${throwable.message ?: "Kotlin script compilation failed"}",
                    line    = throwable.lineNumber.takeIf { it >= 0 },
                    column  = throwable.columnNumber.takeIf { it >= 0 }
                )
            }
            else -> {
                ScriptExecutionResult.Error(
                    message = throwable.message ?: throwable.toString()
                )
            }
        }
    }
}
