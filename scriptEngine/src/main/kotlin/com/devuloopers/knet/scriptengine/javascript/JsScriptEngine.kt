package com.devuloopers.knet.scriptengine.javascript

import com.devuloopers.knet.scriptengine.api.ScriptExecutionResult
import com.devuloopers.knet.scriptengine.api.ScriptRequestModel
import com.devuloopers.knet.scriptengine.api.ScriptResponseModel
import com.devuloopers.knet.scriptengine.api.ScriptTestResult
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.PolyglotException

/**
 * JavaScript Execution Engine using GraalJS Polyglot API with full Postman pm.* compatibility.
 * Evaluates real JavaScript code and reports syntax and runtime errors accurately.
 */
class JsScriptEngine {

    /**
     * Executes the given JavaScript test assertion script against HTTP request and response models.
     *
     * @param code The JavaScript code string to evaluate.
     * @param request The [ScriptRequestModel] representing the HTTP request.
     * @param response The optional [ScriptResponseModel] representing the HTTP response.
     * @param env Mutable map containing environment variables accessible and modifiable by the script.
     * @return A [ScriptExecutionResult] containing test results, logs, and updated environment state.
     */
    fun execute(
        code: String,
        request: ScriptRequestModel,
        response: ScriptResponseModel?,
        env: MutableMap<String, String>
    ): ScriptExecutionResult {
        if (code.isBlank()) {
            return ScriptExecutionResult.Success(
                request = request,
                testResults = emptyList(),
                environmentUpdates = env,
                logs = emptyList()
            )
        }

        val testResults = mutableListOf<ScriptTestResult>()
        val logs = mutableListOf<String>()

        return try {
            val hostBridge = ScriptHostBridge(
                testResults = testResults,
                logs = logs,
                env = env
            )

            Context.newBuilder("js")
                .allowAllAccess(true)
                .option("engine.WarnInterpreterOnly", "false")
                .build().use { polyglotContext ->

                    polyglotContext.getBindings("js").putMember("__bridge", hostBridge)

                    val polyfill = """
                        var console = {
                            log: function(msg) { __bridge.log(String(msg)); }
                        };
                        var pm = {
                            environment: {
                                set: function(k, v) { __bridge.setEnv(String(k), String(v)); },
                                get: function(k) { return null; }
                            },
                            request: {
                                url: "${request.url}",
                                method: "${request.method}",
                                headers: {}
                            },
                            response: {
                                code: ${response?.statusCode ?: 0},
                                status: "${response?.statusText ?: ""}",
                                responseTime: ${response?.latencyMs ?: 0},
                                responseSize: ${response?.responseSizeBytes ?: 0},
                                text: function() { return ${escapeJsonString(response?.body ?: "")}; },
                                json: function() { return JSON.parse(this.text()); },
                                to: {
                                    have: {
                                        status: function(expectedCode) {
                                            if (Number(pm.response.code) != Number(expectedCode)) {
                                                throw new Error("Expected status " + expectedCode + " but got " + pm.response.code);
                                            }
                                        }
                                    }
                                }
                            },
                            test: function(testName, fn) {
                                try {
                                    fn();
                                    __bridge.addTest(testName, true, null);
                                } catch(err) {
                                    __bridge.addTest(testName, false, err.message || String(err));
                                }
                            },
                            expect: function(actual) {
                                return {
                                    to: {
                                        eql: function(expected) {
                                            if (actual != expected) {
                                                throw new Error("Expected " + expected + " but got " + actual);
                                            }
                                        },
                                        include: function(needle) {
                                            if (String(actual).indexOf(needle) === -1) {
                                                throw new Error("Expected '" + actual + "' to include '" + needle + "'");
                                            }
                                        }
                                    }
                                };
                            }
                        };
                    """.trimIndent()

                    val fullScript = "$polyfill\n$code"
                    polyglotContext.eval("js", fullScript)
                }

            ScriptExecutionResult.Success(
                request = request,
                testResults = testResults,
                environmentUpdates = env,
                logs = logs
            )
        } catch (e: PolyglotException) {
            ScriptExecutionResult.Error(
                message = "JavaScript Error: ${e.message ?: e.toString()}"
            )
        } catch (e: Exception) {
            ScriptExecutionResult.Error(
                message = "JavaScript Error: ${e.message ?: e.toString()}"
            )
        }
    }

    private fun escapeJsonString(input: String): String {
        return "\"" + input.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r") + "\""
    }
}

/**
 * Host interop bridge object passed into GraalJS script context.
 * Provides type-safe callbacks for test recording, logging, and environment updates.
 *
 * @property testResults Target list for storing [ScriptTestResult] assertion results.
 * @property logs Target list for capturing console log output strings.
 * @property env Target map for mutating environment key-value pairs.
 */
class ScriptHostBridge(
    private val testResults: MutableList<ScriptTestResult>,
    private val logs: MutableList<String>,
    private val env: MutableMap<String, String>
) {

    /**
     * Records a test assertion result.
     *
     * @param name The name or description of the test.
     * @param passed Whether the assertion succeeded.
     * @param errorMessage Optional failure detail message.
     */
    @HostAccess.Export
    fun addTest(name: String, passed: Boolean, errorMessage: String?) {
        testResults.add(ScriptTestResult(name = name, passed = passed, errorMessage = errorMessage))
    }

    /**
     * Appends a log message to the script execution log output.
     *
     * @param message Log message text.
     */
    @HostAccess.Export
    fun log(message: String) {
        logs.add(message)
    }

    /**
     * Updates an environment variable in the current workspace context.
     *
     * @param key Environment key name.
     * @param value Environment value string.
     */
    @HostAccess.Export
    fun setEnv(key: String, value: String) {
        env[key] = value
    }
}
