package com.devuloopers.knet.engine.script.internal

import com.devuloopers.knet.engine.script.api.EnvironmentStore
import org.graalvm.polyglot.HostAccess

/**
 * Shared host interop bridge exposed to JavaScript execution environments.
 * Methods annotated with [@HostAccess.Export] can be safely invoked from inside the sandboxed script context.
 *
 * @property resultCollector Thread-safe collector for test assertions and logs.
 * @property environment Store providing read/write access to environment variables.
 */
class ScriptHostBridge(
    private val resultCollector: ResultCollector,
    private val environment: EnvironmentStore
) {

    /**
     * Records a test assertion result from script execution.
     *
     * @param name Name or description of the test.
     * @param passed True if assertion passed, false if failed.
     * @param errorMessage Optional failure description message.
     */
    @HostAccess.Export
    fun addTest(name: String, passed: Boolean, errorMessage: String?) {
        resultCollector.addTestResult(name = name, passed = passed, errorMessage = errorMessage)
    }

    /**
     * Captures a log message printed from inside script code.
     *
     * @param message Text string to append to execution logs.
     */
    @HostAccess.Export
    fun log(message: String) {
        resultCollector.addLog(message)
    }

    /**
     * Sets or updates an environment variable key-value pair.
     *
     * @param key Environment variable key name.
     * @param value Environment variable value text.
     */
    @HostAccess.Export
    fun setEnv(key: String, value: String?) {
        if (value == null) {
            environment.remove(key)
        } else {
            environment.set(key, value)
        }
    }

    /**
     * Retrieves an environment variable value by key.
     *
     * @param key Environment variable key name.
     * @return Value string if key exists, or null.
     */
    @HostAccess.Export
    fun getEnv(key: String): String? {
        return environment.get(key)
    }

    /**
     * Checks if an environment variable exists.
     */
    @HostAccess.Export
    fun hasEnv(key: String): Boolean {
        return environment.get(key) != null
    }

    /**
     * Removes an environment variable.
     */
    @HostAccess.Export
    fun unsetEnv(key: String) {
        environment.remove(key)
    }
}
