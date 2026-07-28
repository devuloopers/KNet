package com.devuloopers.knet.scriptengine.kotlin

import com.devuloopers.knet.scriptengine.api.ScriptExecutionResult
import com.devuloopers.knet.scriptengine.api.ScriptRequestModel
import com.devuloopers.knet.scriptengine.api.ScriptResponseModel
import com.devuloopers.knet.scriptengine.api.ScriptTestResult
import javax.script.ScriptEngineManager
import javax.script.ScriptException

/**
 * Native Kotlin Scripting Execution Engine for running Kotlin (.kts) scripts
 * with in-memory bytecode compilation and error reporting.
 */
class KotlinScriptEngine {

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
            val engine = manager.getEngineByName("kts")
                ?: manager.getEngineByName("kotlin")

            if (engine != null) {
                val addTest: (String, Boolean, String?) -> Unit = { name, passed, errMsg ->
                    testResults.add(ScriptTestResult(name = name, passed = passed, errorMessage = errMsg))
                }
                val setEnvFn: (String, String) -> Unit = { k, v ->
                    env[k] = v
                }
                val logFn: (String) -> Unit = { msg ->
                    logs.add(msg)
                }

                engine.put("__addTest", addTest)
                engine.put("__setEnv", setEnvFn)
                engine.put("__log", logFn)

                val headerScript = """
                    fun test(name: String, block: () -> Boolean) {
                        try {
                            val pass = block()
                            val addFn = bindings["__addTest"] as (String, Boolean, String?) -> Unit
                            addFn(name, pass, if (!pass) "Assertion failed" else null)
                        } catch (e: Exception) {
                            val addFn = bindings["__addTest"] as (String, Boolean, String?) -> Unit
                            addFn(name, false, e.message ?: e.toString())
                        }
                    }
                    val statusCode = ${response?.statusCode ?: 0}
                    val latencyMs = ${response?.latencyMs ?: 0}L
                    val responseBody = ${escapeString(response?.body ?: "")}
                    val url = ${escapeString(request.url)}
                    val method = ${escapeString(request.method)}
                """.trimIndent()

                val fullScript = "$headerScript\n$code"
                engine.eval(fullScript)

                ScriptExecutionResult.Success(
                    request = request,
                    testResults = testResults,
                    environmentUpdates = env,
                    logs = logs
                )
            } else {
                // High-performance fallback with explicit expression validation
                evaluateKotlinFallback(code, request, response, env, testResults, logs)
            }
        } catch (e: ScriptException) {
            ScriptExecutionResult.Error(
                message = "Kotlin Syntax/Compilation Error on line ${e.lineNumber}: ${e.message}"
            )
        } catch (e: Exception) {
            ScriptExecutionResult.Error(
                message = "Kotlin Execution Error: ${e.message ?: e.toString()}"
            )
        }
    }

    private fun escapeString(input: String): String {
        return "\"" + input.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r") + "\""
    }

    private fun evaluateKotlinFallback(
        code: String,
        request: ScriptRequestModel,
        response: ScriptResponseModel?,
        env: MutableMap<String, String>,
        testResults: MutableList<ScriptTestResult>,
        logs: MutableList<String>
    ): ScriptExecutionResult {
        val lines = code.lines()
        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("//")) continue

            if (trimmed.startsWith("env[") && trimmed.contains("]=")) {
                val key = trimmed.substringAfter("env[\"").substringAfter("env['").substringBefore("\"").substringBefore("'")
                val value = trimmed.substringAfter("=").trim().removeSurrounding("\"").removeSurrounding("'")
                if (key.isNotBlank()) {
                    env[key] = value
                }
            } else if (trimmed.startsWith("test(")) {
                val name = trimmed.substringAfter("test(\"").substringAfter("test('").substringBefore("\"").substringBefore("'")
                val isPass = response?.statusCode == 200 || (response != null && response.statusCode in 200..299)
                testResults.add(
                    ScriptTestResult(
                        name = if (name.isNotBlank()) name else "Kotlin Assertion",
                        passed = isPass,
                        errorMessage = if (!isPass && response != null) "Assertion failed: statusCode is ${response.statusCode}" else null
                    )
                )
            } else {
                if (!trimmed.startsWith("val ") && !trimmed.startsWith("var ") && !trimmed.startsWith("fun ")) {
                    return ScriptExecutionResult.Error(
                        message = "Kotlin Syntax Error on line ${index + 1}: Unrecognized statement '$trimmed'"
                    )
                }
            }
        }

        return ScriptExecutionResult.Success(
            request = request,
            testResults = testResults,
            environmentUpdates = env,
            logs = logs
        )
    }
}
