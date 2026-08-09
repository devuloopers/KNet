package com.devuloopers.knet.engine.script.javascript

import com.devuloopers.knet.engine.script.api.EnvironmentStore
import com.devuloopers.knet.engine.script.api.ScriptEngine
import com.devuloopers.knet.engine.script.api.ScriptExecutionResult
import com.devuloopers.knet.engine.script.api.ScriptLanguage
import com.devuloopers.knet.engine.script.api.ScriptRequestModel
import com.devuloopers.knet.engine.script.api.ScriptResponseModel
import com.devuloopers.knet.engine.script.internal.ExceptionFormatter
import com.devuloopers.knet.engine.script.internal.ResultCollector
import com.devuloopers.knet.engine.script.internal.ScriptHostBridge
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Engine
import org.graalvm.polyglot.HostAccess

/**
 * Production-grade JavaScript Execution Engine powering KNet API Studio via GraalJS.
 * Reuses a single shared application-wide Graal [Engine] while instantiating per-execution isolated sandboxed [Context] instances.
 */
class GraalJsScriptEngine : ScriptEngine {

    override val language: ScriptLanguage = ScriptLanguage.JAVASCRIPT

    private val sharedEngine: Engine = Engine.newBuilder()
        .option("engine.WarnInterpreterOnly", "false")
        .build()

    /**
     * Evaluates a JavaScript script against request, response, and environment parameters within an isolated sandboxed polyglot context.
     *
     * @param code JavaScript source code string.
     * @param request HTTP request model exposed to script.
     * @param response Optional HTTP response model exposed to script.
     * @param environment Thread-safe [EnvironmentStore] providing variable management.
     * @return Execution outcome [ScriptExecutionResult].
     */
    override suspend fun execute(
        code: String,
        request: ScriptRequestModel,
        response: ScriptResponseModel?,
        environment: EnvironmentStore
    ): ScriptExecutionResult {
        if (code.isBlank()) {
            return ScriptExecutionResult.Success(
                request = request,
                testResults = emptyList(),
                environmentUpdates = environment.snapshot(),
                logs = emptyList()
            )
        }

        val resultCollector = ResultCollector()
        val hostBridge = ScriptHostBridge(resultCollector = resultCollector, environment = environment)

        var currentPolyglotContext: Context? = null

        return try {
            val polyglotContext = Context.newBuilder("js")
                .engine(sharedEngine)
                .allowHostAccess(HostAccess.EXPLICIT)
                .allowIO(false)
                .allowNativeAccess(false)
                .allowCreateThread(false)
                .allowCreateProcess(false)
                .build()

            currentPolyglotContext = polyglotContext

            polyglotContext.use { activeContext ->
                val bindings = activeContext.getBindings("js")
                bindings.putMember("__bridge", hostBridge)

                val consoleSource = ScriptResourceLoader.loadSource("/runtime/console.js")
                val expectSource = ScriptResourceLoader.loadSource("/runtime/expect.js")
                activeContext.eval(consoleSource)
                activeContext.eval(expectSource)

                val pmPolyfill = """
                    var pm = {
                        environment: {
                            set: function(k, v) { __bridge.setEnv(String(k), String(v)); },
                            get: function(k) { return __bridge.getEnv(String(k)); },
                            has: function(k) { return __bridge.hasEnv(String(k)); },
                            unset: function(k) { __bridge.unsetEnv(String(k)); }
                        },
                        variables: {
                            set: function(k, v) { __bridge.setEnv(String(k), String(v)); },
                            get: function(k) { return __bridge.getEnv(String(k)); },
                            has: function(k) { return __bridge.hasEnv(String(k)); }
                        },
                        request: {
                            url: "${request.url}",
                            method: "${request.method}",
                            headers: {
                                add: function(headerObj) {
                                    if (headerObj && headerObj.key) {
                                        this[headerObj.key] = headerObj.value;
                                    }
                                }
                            }
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
                            return expect(actual);
                        }
                    };
                """.trimIndent()

                activeContext.eval("js", pmPolyfill)
                activeContext.eval("js", code)

                ScriptExecutionResult.Success(
                    request = request,
                    testResults = resultCollector.getTestResults(),
                    environmentUpdates = environment.snapshot(),
                    logs = resultCollector.getLogs()
                )
            }
        } catch (t: Throwable) {
            currentPolyglotContext?.close(true)
            ExceptionFormatter.format(t)
        }
    }

    private fun escapeJsonString(input: String): String {
        return "\"" + input
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t") + "\""
    }
}
