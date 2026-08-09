package com.devuloopers.knet.engine.script.kotlin.runtime

import com.devuloopers.knet.engine.script.api.EnvironmentStore
import com.devuloopers.knet.engine.script.api.ScriptExecutionResult
import com.devuloopers.knet.engine.script.api.ScriptRequestModel
import com.devuloopers.knet.engine.script.api.ScriptResponseModel
import com.devuloopers.knet.engine.script.internal.BindingsProvider
import com.devuloopers.knet.engine.script.internal.ExceptionFormatter
import com.devuloopers.knet.engine.script.internal.ResultCollector
import com.devuloopers.knet.engine.script.internal.ResultCollectorHolder
import javax.script.ScriptEngineManager
import javax.script.SimpleBindings

/**
 * Production-grade Native Kotlin Runtime executing dynamic Kotlin (.kts) scripts
 * via JSR-223 direct evaluation.
 *
 * Note: Compiled script caching via [javax.script.Compilable] was intentionally removed.
 * The Kotlin JSR-223 engine bakes the first [SimpleBindings] snapshot into the compiled
 * [javax.script.CompiledScript] object. On subsequent calls with a fresh [EnvironmentStore],
 * the compiled script ignores the new bindings and reads from the stale first-run snapshot.
 * Using [javax.script.ScriptEngine.eval] directly avoids this binding reuse entirely,
 * guaranteeing every execution receives a fresh environment.
 */
class NativeKotlinRuntime : KotlinRuntime {

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

            // Always eval directly — never use CompiledScript.eval() which bakes in stale bindings.
            engine.eval(fullScript, bindings)

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
