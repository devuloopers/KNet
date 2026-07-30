package com.devuloopers.knet.scriptengine.core

import com.devuloopers.knet.scriptengine.api.ScriptExecutionResult
import org.graalvm.polyglot.PolyglotException

/**
 * Standardized exception formatter converting engine-specific exceptions into uniform error result models.
 */
object ExceptionFormatter {

    /**
     * Formats a raw exception into a structured [ScriptExecutionResult.Error].
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
            else -> {
                ScriptExecutionResult.Error(
                    message = throwable.message ?: throwable.toString()
                )
            }
        }
    }
}
