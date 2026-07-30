package com.devuloopers.knet.scriptengine.javascript

import com.devuloopers.knet.scriptengine.api.EnvironmentStore
import com.devuloopers.knet.scriptengine.api.ScriptEngine
import com.devuloopers.knet.scriptengine.api.ScriptExecutionResult
import com.devuloopers.knet.scriptengine.api.ScriptLanguage
import com.devuloopers.knet.scriptengine.api.ScriptRequestModel
import com.devuloopers.knet.scriptengine.api.ScriptResponseModel
import com.devuloopers.knet.scriptengine.bridge.ScriptHostBridge
import com.devuloopers.knet.scriptengine.core.ExceptionFormatter
import com.devuloopers.knet.scriptengine.core.ResultCollector
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

                // Evaluate pre-parsed static runtime polyfills
                val consoleSource = RuntimeLoader.loadSource("/runtime/console.js")
                val expectSource = RuntimeLoader.loadSource("/runtime/expect.js")
                activeContext.eval(consoleSource)
                activeContext.eval(expectSource)

                // Inject dynamic pm.* API polyfill
                val pmPolyfill = """
                    var pm = {
                        environment: {
                            set: function(k, v) { __bridge.setEnv(String(k), String(v)); },
                            get: function(k) { return __bridge.getEnv(String(k)); }
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
                            return expect(actual);
                        }
                    };
                """.trimIndent()

                activeContext.eval("js", pmPolyfill)
                activeContext.eval("js", code)
            }

            ScriptExecutionResult.Success(
                request = request,
                testResults = resultCollector.getTestResults(),
                environmentUpdates = environment.snapshot(),
                logs = resultCollector.getLogs()
            )
        } catch (throwable: Throwable) {
            currentPolyglotContext?.close(true)
            ExceptionFormatter.format(throwable)
        }
    }

    private fun escapeJsonString(input: String): String {
        return "\"" + input.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r") + "\""
    }
}
