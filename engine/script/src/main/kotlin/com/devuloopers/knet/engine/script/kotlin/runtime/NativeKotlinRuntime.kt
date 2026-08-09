package com.devuloopers.knet.engine.script.kotlin.runtime

import com.devuloopers.knet.engine.script.api.EnvironmentStore
import com.devuloopers.knet.engine.script.api.ScriptExecutionResult
import com.devuloopers.knet.engine.script.api.ScriptRequestModel
import com.devuloopers.knet.engine.script.api.ScriptResponseModel
import com.devuloopers.knet.engine.script.internal.BindingsProvider
import com.devuloopers.knet.engine.script.internal.CompiledScriptCache
import com.devuloopers.knet.engine.script.internal.ExceptionFormatter
import com.devuloopers.knet.engine.script.internal.ResultCollector
import com.devuloopers.knet.engine.script.internal.ResultCollectorHolder
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

        ResultCollectorHolder.set(resultCollector)
        return try {
            val engine = engineManager.getEngineByName("kts")
                ?: engineManager.getEngineByName("kotlin")
                ?: return ScriptExecutionResult.Error(message = "Native Kotlin JSR-223 Engine factory not registered.")

            val bindings = SimpleBindings(bindingsProvider.createBindingsMap())
            engine.setBindings(bindings, javax.script.ScriptContext.ENGINE_SCOPE)

            val headerScript = """
                object console {
                    fun log(msg: Any?) {
                        com.devuloopers.knet.engine.script.internal.ResultCollectorHolder.get()?.addLog(msg?.toString() ?: "null")
                    }
                    fun info(msg: Any?) = log(msg)
                    fun warn(msg: Any?) = log(msg)
                    fun error(msg: Any?) = log(msg)
                }
                fun println(msg: Any?) = console.log(msg)
                class ExpectValue(val value: Any?) {
                    fun toNotBeNull(): Boolean = value != null && value.toString().isNotBlank()
                    fun toBe(expected: Any?): Boolean = value == expected
                }
                fun expect(value: Any?): ExpectValue = ExpectValue(value)
                fun test(name: String, block: () -> Boolean) {
                    val start = System.currentTimeMillis()
                    val collector = com.devuloopers.knet.engine.script.internal.ResultCollectorHolder.get()
                    try {
                        val passed = block()
                        val duration = System.currentTimeMillis() - start
                        collector?.addTestResult(name, passed, if (passed) null else "Assertion failed", duration)
                    } catch (e: Throwable) {
                        val duration = System.currentTimeMillis() - start
                        collector?.addTestResult(name, false, e.message ?: e.toString(), duration)
                    }
                }
            """.trimIndent()

            val fullScript = "$headerScript\n$code"

            val compilableEngine = engine as? Compilable
            if (compilableEngine != null) {
                val compiled = scriptCache.get(fullScript) ?: run {
                    val c = compilableEngine.compile(fullScript)
                    scriptCache.put(fullScript, c)
                    c
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
        } catch (t: Throwable) {
            ExceptionFormatter.format(t)
        } finally {
            ResultCollectorHolder.clear()
        }
    }
}
