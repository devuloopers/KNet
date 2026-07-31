package com.devuloopers.knet.scriptengine.kotlin.runtime

import com.devuloopers.knet.scriptengine.api.EnvironmentStore
import com.devuloopers.knet.scriptengine.api.ScriptExecutionResult
import com.devuloopers.knet.scriptengine.api.ScriptRequestModel
import com.devuloopers.knet.scriptengine.api.ScriptResponseModel
import com.devuloopers.knet.scriptengine.core.BindingsProvider
import com.devuloopers.knet.scriptengine.core.ExceptionFormatter
import com.devuloopers.knet.scriptengine.core.ResultCollector
import javax.script.Compilable
import javax.script.ScriptEngineManager
import javax.script.SimpleBindings

/**
 * Production-grade Native Kotlin Runtime executing dynamic Kotlin (.kts) scripts
 * via JSR-223 bytecode compilation with compiled script caching.
 */
class NativeKotlinRuntime(
    private val scriptCache: CompiledScriptCache = CompiledScriptCache()
) : KotlinRuntime {

    private val engineManager = ScriptEngineManager()

    override fun isAvailable(): Boolean {
        return try {
            val engine = engineManager.getEngineByName("kts") ?: engineManager.getEngineByName("kotlin")
            engine != null
        } catch (_: Throwable) {
            false
        }
    }

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
        val bindingsProvider = BindingsProvider(request, response, environment, resultCollector)

        return try {
            val engine = engineManager.getEngineByName("kts")
                ?: engineManager.getEngineByName("kotlin")
                ?: return ScriptExecutionResult.Error(message = "Native Kotlin JSR-223 Engine factory not registered.")

            val bindings = SimpleBindings(bindingsProvider.createBindingsMap())
            engine.setBindings(bindings, javax.script.ScriptContext.ENGINE_SCOPE)

            val headerScript = if (response != null) """
                fun test(name: String, block: () -> Boolean) {
                    try {
                        val pass = block()
                        resultCollector.addTestResult(name, pass, if (!pass) "Assertion failed" else null)
                    } catch (e: Exception) {
                        resultCollector.addTestResult(name, false, e.message ?: e.toString())
                    }
                }
                val statusCode = response.statusCode
                val latencyMs = response.latencyMs
                val responseBody = response.body
                val url = request.url
                val method = request.method
            """.trimIndent() else """
                fun test(name: String, block: () -> Boolean) {
                    try {
                        val pass = block()
                        resultCollector.addTestResult(name, pass, if (!pass) "Assertion failed" else null)
                    } catch (e: Exception) {
                        resultCollector.addTestResult(name, false, e.message ?: e.toString())
                    }
                }
                val statusCode = 0
                val latencyMs = 0L
                val responseBody = ""
                val url = request.url
                val method = request.method
            """.trimIndent()

            val fullScript = "$headerScript\n$code"

            if (engine is Compilable) {
                val compiled = scriptCache.get(fullScript) ?: run {
                    val newCompiled = engine.compile(fullScript)
                    scriptCache.put(fullScript, newCompiled)
                    newCompiled
                }
                compiled.eval(bindings)
            } else {
                engine.eval(fullScript, bindings)
            }

            ScriptExecutionResult.Success(
                request = request,
                testResults = resultCollector.getTestResults(),
                environmentUpdates = environment.snapshot(),
                logs = resultCollector.getLogs()
            )
        } catch (throwable: Throwable) {
            ExceptionFormatter.format(throwable)
        }
    }
}
