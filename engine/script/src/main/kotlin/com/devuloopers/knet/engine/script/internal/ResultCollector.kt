package com.devuloopers.knet.engine.script.internal

import com.devuloopers.knet.scripting.model.ScriptAssertion
import java.util.concurrent.atomic.AtomicReference

/**
 * Thread-safe accumulator for collecting test assertion results, console logs, and duration metrics.
 * Utilizes atomic references for lock-free state mutation under concurrent execution.
 */
class ResultCollector {

    private val testResultsRef = AtomicReference<List<ScriptAssertion>>(emptyList())
    private val logsRef = AtomicReference<List<String>>(emptyList())

    /**
     * Records a test assertion result atomically.
     *
     * @param name Name of the test assertion.
     * @param passed Whether the assertion passed.
     * @param errorMessage Optional failure description.
     * @param durationMs Duration of assertion in milliseconds.
     */
    fun addTestResult(name: String, passed: Boolean, errorMessage: String?, durationMs: Long = 0L) {
        val result = ScriptAssertion(
            name = name,
            passed = passed,
            errorMessage = errorMessage,
            durationMillis = durationMs,
        )
        while (true) {
            val current = testResultsRef.get()
            val updated = current + result
            if (testResultsRef.compareAndSet(current, updated)) break
        }
    }

    /**
     * Appends a log line to the captured console output list atomically.
     *
     * @param message Log message text.
     */
    fun addLog(message: String) {
        while (true) {
            val current = logsRef.get()
            val updated = current + message
            if (logsRef.compareAndSet(current, updated)) break
        }
    }

    /**
     * Returns an immutable snapshot list of all recorded test assertion results.
     *
     * @return Immutable list of [ScriptAssertion].
     */
    fun getTestResults(): List<ScriptAssertion> = testResultsRef.get()

    /**
     * Returns an immutable snapshot list of all captured console logs.
     *
     * @return Immutable list of log strings.
     */
    fun getLogs(): List<String> = logsRef.get()
}
