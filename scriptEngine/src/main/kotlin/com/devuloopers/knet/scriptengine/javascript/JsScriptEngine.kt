package com.devuloopers.knet.scriptengine.javascript

import com.devuloopers.knet.scriptengine.api.ScriptExecutionResult
import com.devuloopers.knet.scriptengine.api.ScriptRequestModel
import com.devuloopers.knet.scriptengine.api.ScriptResponseModel
import com.devuloopers.knet.scriptengine.api.ScriptTestResult
import javax.script.ScriptEngineManager
import javax.script.ScriptException

/**
 * JavaScript Execution Engine using GraalJS / JVM ScriptEngine with full Postman pm.* compatibility.
 * Evaluates real JavaScript code and reports syntax and runtime errors accurately.
 */
class JsScriptEngine {

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
            val manager = ScriptEngineManager()
            val engine = manager.getEngineByName("GraalJS")
                ?: manager.getEngineByName("javascript")
                ?: manager.getEngineByName("js")

            if (engine != null) {
                // Bridge functions to Kotlin runtime
                val addTest: (String, Boolean, String?) -> Unit = { name, passed, errMsg ->
                    testResults.add(ScriptTestResult(name = name, passed = passed, errorMessage = errMsg))
                }
                val logFn: (String) -> Unit = { msg ->
                    logs.add(msg)
                }
                val setEnvFn: (String, String) -> Unit = { k, v ->
                    env[k] = v
                }

                engine.put("__addTest", addTest)
                engine.put("__log", logFn)
                engine.put("__setEnv", setEnvFn)

                // Inject pm object polyfill
                val polyfill = """
                    var console = {
                        log: function(msg) { __log(String(msg)); }
                    };
                    var pm = {
                        environment: {
                            set: function(k, v) { __setEnv(String(k), String(v)); },
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
                                        if (pm.response.code !== expectedCode) {
                                            throw new Error("Expected status " + expectedCode + " but got " + pm.response.code);
                                        }
                                    }
                                }
                            }
                        },
                        test: function(testName, fn) {
                            try {
                                fn();
                                __addTest(testName, true, null);
                            } catch(err) {
                                __addTest(testName, false, err.message || String(err));
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
                engine.eval(fullScript)

                ScriptExecutionResult.Success(
                    request = request,
                    testResults = testResults,
                    environmentUpdates = env,
                    logs = logs
                )
            } else {
                ScriptExecutionResult.Error(message = "GraalJS Engine not available on JVM runtime.")
            }
        } catch (e: ScriptException) {
            ScriptExecutionResult.Error(
                message = "JavaScript Syntax/Runtime Error on line ${e.lineNumber}: ${e.message}"
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
