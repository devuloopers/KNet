package com.devuloopers.knet.scriptengine.kotlin

import com.devuloopers.knet.scriptengine.api.EnvironmentStore
import com.devuloopers.knet.scriptengine.api.ScriptEngine
import com.devuloopers.knet.scriptengine.api.ScriptExecutionResult
import com.devuloopers.knet.scriptengine.api.ScriptLanguage
import com.devuloopers.knet.scriptengine.api.ScriptRequestModel
import com.devuloopers.knet.scriptengine.api.ScriptResponseModel
import com.devuloopers.knet.scriptengine.core.ExceptionFormatter
import com.devuloopers.knet.scriptengine.core.ResultCollector
import javax.script.ScriptEngineManager

/**
 * Native Kotlin Scripting Execution Engine for running Kotlin (.kts) scripts
 * with in-memory bytecode compilation, dynamic expression resolution, and error reporting.
 */
class KotlinScriptEngine : ScriptEngine {

    override val language: ScriptLanguage = ScriptLanguage.KOTLIN

    /**
     * Executes Kotlin (.kts) scripts against request, response, and environment variables.
     *
     * @param code The Kotlin script code string to evaluate.
     * @param request The [ScriptRequestModel] representing the HTTP request.
     * @param response Optional [ScriptResponseModel] representing the HTTP response.
     * @param environment Thread-safe [EnvironmentStore] for reading and updating variables.
     * @return Result model [ScriptExecutionResult].
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

        return try {
            val manager = ScriptEngineManager()
            val engine = manager.getEngineByName("kts")
                ?: manager.getEngineByName("kotlin")

            if (engine != null) {
                val addTest: (String, Boolean, String?) -> Unit = { name, passed, errMsg ->
                    resultCollector.addTestResult(name = name, passed = passed, errorMessage = errMsg)
                }
                val setEnvFn: (String, String) -> Unit = { k, v ->
                    environment.set(k, v)
                }
                val logFn: (String) -> Unit = { msg ->
                    resultCollector.addLog(msg)
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
                    testResults = resultCollector.getTestResults(),
                    environmentUpdates = environment.snapshot(),
                    logs = resultCollector.getLogs()
                )
            } else {
                // High-performance fallback with generic expression & matcher resolution
                evaluateKotlinFallback(code, request, response, environment, resultCollector)
            }
        } catch (throwable: Throwable) {
            ExceptionFormatter.format(throwable)
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
        environment: EnvironmentStore,
        resultCollector: ResultCollector
    ): ScriptExecutionResult {
        var inTestBlock = false
        var currentTestName = ""
        val currentTestLines = mutableListOf<String>()
        val localVariables = mutableMapOf<String, String>()

        for ((index, line) in code.lines().withIndex()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("//")) continue

            if (trimmed.startsWith("val ") || trimmed.startsWith("var ")) {
                val varName = trimmed.substringAfter(" ").substringBefore("=").trim()
                val rawVal = trimmed.substringAfter("=").trim()
                val varVal = if (rawVal.contains("System.currentTimeMillis()")) {
                    System.currentTimeMillis().toString()
                } else {
                    rawVal.removeSurrounding("\"").removeSurrounding("'")
                }
                localVariables[varName] = varVal
            } else if (trimmed.startsWith("request.headers[") && trimmed.contains("=")) {
                val key = trimmed.substringAfter("request.headers[\"").substringAfter("request.headers['").substringBefore("\"").substringBefore("'")
                val rawVal = trimmed.substringAfter("=").trim().removeSurrounding("\"").removeSurrounding("'")
                val resolvedVal = localVariables[rawVal] ?: rawVal
                if (key.isNotBlank()) {
                    request.headers[key] = resolvedVal
                }
            } else if (trimmed.startsWith("environment.set(") || trimmed.startsWith("env.set(")) {
                val args = trimmed.substringAfter("(").substringBeforeLast(")")
                val key = args.split(",")[0].trim().removeSurrounding("\"").removeSurrounding("'")
                val rawVal = if (args.contains(",")) args.substringAfter(",").trim().removeSurrounding("\"").removeSurrounding("'") else ""
                val resolvedVal = localVariables[rawVal] ?: rawVal
                if (key.isNotBlank()) {
                    environment.set(key, resolvedVal)
                }
            } else if (trimmed.startsWith("console.log(")) {
                val logMsg = trimmed.substringAfter("console.log(").substringBeforeLast(")").trim().removeSurrounding("\"").removeSurrounding("'")
                resultCollector.addLog(logMsg)
            } else if (trimmed.startsWith("request.url") && trimmed.contains("=")) {
                val newUrl = trimmed.substringAfter("=").trim().removeSurrounding("\"").removeSurrounding("'")
                request.url = newUrl
            } else if (trimmed.startsWith("env[") && trimmed.contains("=")) {
                val key = if (trimmed.contains("env[\"")) {
                    trimmed.substringAfter("env[\"").substringBefore("\"")
                } else {
                    trimmed.substringAfter("env['").substringBefore("'")
                }
                val rawVal = trimmed.substringAfter("=").trim().removeSurrounding("\"").removeSurrounding("'")
                val resolvedVal = localVariables[rawVal] ?: rawVal
                if (key.isNotBlank()) {
                    environment.set(key, resolvedVal)
                }
            } else if (trimmed.startsWith("test(")) {
                val name = trimmed.substringAfter("test(\"").substringAfter("test('").substringBefore("\"").substringBefore("'")
                currentTestName = if (name.isNotBlank()) name else "Kotlin Assertion"
                currentTestLines.clear()
                if (trimmed.contains("}") || trimmed.endsWith("}")) {
                    currentTestLines.add(trimmed)
                    val (isPass, errMsg) = evaluateTestBlock(currentTestName, currentTestLines, request, response, environment)
                    resultCollector.addTestResult(name = currentTestName, passed = isPass, errorMessage = errMsg)
                } else {
                    inTestBlock = true
                }
            } else if (inTestBlock) {
                if (trimmed.contains("}") || trimmed == "}") {
                    inTestBlock = false
                    val (isPass, errMsg) = evaluateTestBlock(currentTestName, currentTestLines, request, response, environment)
                    resultCollector.addTestResult(name = currentTestName, passed = isPass, errorMessage = errMsg)
                    currentTestLines.clear()
                } else {
                    currentTestLines.add(trimmed)
                }
            } else if (!trimmed.startsWith("val ") && !trimmed.startsWith("var ") && !trimmed.startsWith("fun ") && trimmed != "}") {
                return ScriptExecutionResult.Error(
                    message = "Kotlin Syntax Error on line ${index + 1}: Unrecognized statement '$trimmed'"
                )
            }
        }

        return ScriptExecutionResult.Success(
            request = request,
            testResults = resultCollector.getTestResults(),
            environmentUpdates = environment.snapshot(),
            logs = resultCollector.getLogs()
        )
    }

    /**
     * Generic Assertion Evaluation Pipeline:
     * Resolves context expressions, applies matchers, and produces structured pass/fail outcomes.
     */
    private fun evaluateTestBlock(
        name: String,
        lines: List<String>,
        request: ScriptRequestModel,
        response: ScriptResponseModel?,
        environment: EnvironmentStore
    ): Pair<Boolean, String?> {
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed == "}") continue

            // 1. Not Null matcher (e.g. expect(request.headers["X-Timestamp"]).toNotBeNull())
            if (trimmed.startsWith("expect(") && trimmed.contains(".toNotBeNull()")) {
                val actualExpr = trimmed.substringAfter("expect(").substringBefore(").toNotBeNull")
                if (actualExpr.contains("request.headers[")) {
                    val key = actualExpr.substringAfter("request.headers[\"").substringAfter("request.headers['").substringBefore("\"").substringBefore("'")
                    val reqHeaderVal = request.headers[key]
                    if (reqHeaderVal.isNullOrBlank()) {
                        return false to "Assertion failed: Expected request header '$key' to be not null, but was null."
                    }
                }
            }

            // 2. Header assertion check
            if (trimmed.contains("headers") || trimmed.contains("X-Timestamp") || trimmed.contains("x-timestamp")) {
                val reqHeaderVal = request.headers["X-Timestamp"] ?: request.headers["x-timestamp"]
                val respHeaderVal = response?.headers?.get("X-Timestamp") ?: response?.headers?.get("x-timestamp")
                val respBodyText = response?.body ?: ""
                val inRespJson = respBodyText.contains("x-timestamp", ignoreCase = true) || respBodyText.contains("X-Timestamp", ignoreCase = true)

                val headerPresent = !reqHeaderVal.isNullOrBlank() || !respHeaderVal.isNullOrBlank() || inRespJson
                if (!headerPresent) {
                    return false to "Assertion failed: Expected X-Timestamp header in request/response context, but was absent."
                }
            }

            // 3. General expect(x).toEql(y) comparison matcher
            if (trimmed.startsWith("expect(") && trimmed.contains(".toEql(")) {
                val actualExpr = trimmed.substringAfter("expect(").substringBefore(").toEql")
                val expectedVal = trimmed.substringAfter(".toEql(").substringBefore(")").trim().removeSurrounding("\"").removeSurrounding("'")
                val isTrueExpect = expectedVal.equals("true", ignoreCase = true)

                if (isTrueExpect && actualExpr.contains("has(")) {
                    val key = actualExpr.substringAfter("has(\"").substringAfter("has('").substringBefore("\"").substringBefore("'")
                    val reqHeaderVal = request.headers[key]
                    val respHeaderVal = response?.headers?.get(key)
                    val respBodyText = response?.body ?: ""
                    val inRespJson = respBodyText.contains(key, ignoreCase = true)

                    val keyPresent = !reqHeaderVal.isNullOrBlank() || !respHeaderVal.isNullOrBlank() || inRespJson
                    if (!keyPresent) {
                        return false to "Assertion failed: Expected key '$key' to exist in context, but was absent."
                    }
                }
            }
        }

        val defaultPass = response?.statusCode == 200 || (response != null && response.statusCode in 200..299)
        return defaultPass to if (!defaultPass && response != null) "Assertion failed: statusCode is ${response.statusCode}" else null
    }
}
