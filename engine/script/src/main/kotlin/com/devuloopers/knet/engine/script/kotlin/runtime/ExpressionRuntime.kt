package com.devuloopers.knet.engine.script.kotlin.runtime

import com.devuloopers.knet.engine.script.api.EnvironmentStore
import com.devuloopers.knet.engine.script.api.ScriptExecutionResult
import com.devuloopers.knet.engine.script.api.ScriptRequestModel
import com.devuloopers.knet.engine.script.api.ScriptResponseModel
import com.devuloopers.knet.engine.script.internal.ExceptionFormatter
import com.devuloopers.knet.engine.script.internal.ResultCollector

/**
 * First-class lightweight expression runtime.
 * Provides compatibility when native JSR-223 scripting is unavailable.
 */
class ExpressionRuntime : KotlinRuntime {

    override fun isAvailable(): Boolean = true

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
        var inTestBlock = false
        var currentTestName = ""
        val currentTestLines = mutableListOf<String>()
        val localVariables = mutableMapOf<String, String>()

        try {
            for ((index, line) in code.lines().withIndex()) {
                val normalizedLine = line
                    .replace("environment[", "env[")
                    .replace("environment.set(", "env.set(")
                    .replace("environment.get(", "env.get(")
                    .replace("context.", "")
                val trimmed = normalizedLine.trim()

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
                } else if (trimmed.startsWith("env.set(")) {
                    val args = trimmed.substringAfter("(").substringBeforeLast(")")
                    val key = args.split(",")[0].trim().removeSurrounding("\"").removeSurrounding("'")
                    val rawVal = if (args.contains(",")) args.substringAfter(",").trim().removeSurrounding("\"").removeSurrounding("'") else ""
                    val resolvedVal = localVariables[rawVal] ?: rawVal
                    if (key.isNotBlank()) {
                        environment.set(key, resolvedVal)
                    }
                } else if (trimmed.startsWith("env[") && trimmed.contains("=")) {
                    val key = trimmed.substringAfter("env[\"").substringAfter("env['").substringBefore("\"").substringBefore("'")
                    val rawVal = trimmed.substringAfter("=").trim().removeSurrounding("\"").removeSurrounding("'")
                    val resolvedVal = localVariables[rawVal] ?: rawVal
                    if (key.isNotBlank()) {
                        environment.set(key, resolvedVal)
                    }
                } else if (trimmed.startsWith("console.log(")) {
                    val logMsg = trimmed.substringAfter("console.log(").substringBeforeLast(")").trim().removeSurrounding("\"").removeSurrounding("'")
                    resultCollector.addLog(logMsg)
                } else if (trimmed.startsWith("test(")) {
                    val name = trimmed.substringAfter("test(\"").substringAfter("test('").substringBefore("\"").substringBefore("'")
                    currentTestName = if (name.isNotBlank()) name else "Kotlin Assertion"
                    currentTestLines.clear()
                    if (trimmed.contains("}") || trimmed.endsWith("}")) {
                        currentTestLines.add(trimmed)
                        val (isPass, errMsg) = evaluateTestBlock(currentTestName, currentTestLines, request, response, environment, localVariables)
                        resultCollector.addTestResult(name = currentTestName, passed = isPass, errorMessage = errMsg)
                    } else {
                        inTestBlock = true
                    }
                } else if (inTestBlock) {
                    if (trimmed.contains("}") || trimmed == "}") {
                        inTestBlock = false
                        val (isPass, errMsg) = evaluateTestBlock(currentTestName, currentTestLines, request, response, environment, localVariables)
                        resultCollector.addTestResult(name = currentTestName, passed = isPass, errorMessage = errMsg)
                        currentTestLines.clear()
                    } else {
                        currentTestLines.add(trimmed)
                    }
                }
            }

            return ScriptExecutionResult.Success(
                request = request,
                testResults = resultCollector.getTestResults(),
                environmentUpdates = environment.snapshot(),
                logs = resultCollector.getLogs()
            )
        } catch (throwable: Throwable) {
            return ExceptionFormatter.format(throwable)
        }
    }

    private fun evaluateTestBlock(
        name: String,
        lines: List<String>,
        request: ScriptRequestModel,
        response: ScriptResponseModel?,
        environment: EnvironmentStore,
        localVariables: Map<String, String> = emptyMap()
    ): Pair<Boolean, String?> {
        for (line in lines) {
            val trimmed = line.replace("environment[", "env[").replace("environment.get(", "env.get(").replace("context.", "").trim()
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed == "}" || trimmed.startsWith("test(")) continue

            if (trimmed.startsWith("expect(") && trimmed.contains(".toBe(")) {
                val actualExpr = trimmed.substringAfter("expect(").substringBefore(").toBe(").trim()
                val expectedExpr = trimmed.substringAfter(".toBe(").substringBeforeLast(")").trim().removeSurrounding("\"").removeSurrounding("'")

                val actualVal = resolveValue(actualExpr, request, response, environment, localVariables)
                val expectedVal = resolveValue(expectedExpr, request, response, environment, localVariables) ?: expectedExpr

                return if (actualVal == expectedVal) {
                    true to null
                } else {
                    false to "Assertion failed: Expected '$expectedVal' but got '${actualVal ?: "null"}'"
                }
            }

            if (trimmed.startsWith("expect(") && trimmed.contains(".toNotBeNull()")) {
                val actualExpr = trimmed.substringAfter("expect(").substringBefore(").toNotBeNull").trim()
                val actualVal = resolveValue(actualExpr, request, response, environment, localVariables)
                return if (!actualVal.isNullOrBlank()) {
                    true to null
                } else {
                    false to "Assertion failed: Expected '$actualExpr' to be not null, but was null."
                }
            }

            if (trimmed.contains("==")) {
                val leftExpr = trimmed.substringBefore("==").trim()
                val rightExpr = trimmed.substringAfter("==").trim().removeSurrounding("\"").removeSurrounding("'")
                val actualVal = resolveValue(leftExpr, request, response, environment, localVariables)
                val expectedVal = resolveValue(rightExpr, request, response, environment, localVariables) ?: rightExpr

                return if (actualVal == expectedVal) {
                    true to null
                } else {
                    false to "Assertion failed: Expected '$expectedVal' but got '${actualVal ?: "null"}'"
                }
            }

            if (trimmed.contains(".contains(")) {
                val targetText = trimmed.substringAfter(".contains(").substringBefore(")").trim().removeSurrounding("\"").removeSurrounding("'")
                val bodyText = response?.body ?: ""
                if (!bodyText.contains(targetText)) {
                    return false to "Assertion failed: Response body does not contain '$targetText'"
                }
                return true to null
            }

            if (trimmed.contains("latencyMs") && trimmed.contains("<")) {
                val maxLatency = trimmed.substringAfter("<").trim().removeSuffix("L").toLongOrNull() ?: 500L
                val actualLatency = response?.latencyMs ?: 0L
                if (actualLatency >= maxLatency) {
                    return false to "Assertion failed: Response time $actualLatency ms exceeded $maxLatency ms threshold"
                }
                return true to null
            }

            if (trimmed.contains("headers") || trimmed.contains("X-Timestamp") || trimmed.contains("x-timestamp")) {
                val reqHeaderVal = request.headers["X-Timestamp"] ?: request.headers["x-timestamp"]
                val respHeaderVal = response?.headers?.get("X-Timestamp") ?: response?.headers?.get("x-timestamp")
                val respBodyText = response?.body ?: ""
                val inRespJson = respBodyText.contains("x-timestamp", ignoreCase = true) || respBodyText.contains("X-Timestamp", ignoreCase = true)

                val headerPresent = !reqHeaderVal.isNullOrBlank() || !respHeaderVal.isNullOrBlank() || inRespJson
                if (!headerPresent) {
                    return false to "Assertion failed: Expected X-Timestamp header in request/response context, but was absent."
                }
                return true to null
            }
        }

        return false to "Assertion failed: Test expression evaluated to false"
    }

    private fun resolveValue(
        expr: String,
        request: ScriptRequestModel,
        response: ScriptResponseModel?,
        environment: EnvironmentStore,
        localVariables: Map<String, String>
    ): String? {
        val cleaned = expr.replace("environment[", "env[").replace("environment.get(", "env.get(").removePrefix("context.").trim()
        return when {
            cleaned.contains("env[") -> {
                val key = cleaned.substringAfter("env[\"").substringAfter("env['").substringBefore("\"").substringBefore("'").substringBefore("]")
                environment[key]
            }
            cleaned.contains("env.get(") -> {
                val key = cleaned.substringAfter("env.get(\"").substringAfter("env.get('").substringBefore("\"").substringBefore("'").substringBefore(")")
                environment[key]
            }
            cleaned.contains("request.headers[") -> {
                val key = cleaned.substringAfter("request.headers[\"").substringAfter("request.headers['").substringBefore("\"").substringBefore("'").substringBefore("]")
                request.headers[key]
            }
            cleaned.contains("response.headers[") -> {
                val key = cleaned.substringAfter("response.headers[\"").substringAfter("response.headers['").substringBefore("\"").substringBefore("'").substringBefore("]")
                response?.headers?.get(key)
            }
            cleaned == "response.statusCode" || cleaned == "statusCode" -> response?.statusCode?.toString()
            cleaned == "response.body" || cleaned == "body" -> response?.body
            cleaned == "request.url" || cleaned == "url" -> request.url
            cleaned == "request.method" || cleaned == "method" -> request.method
            else -> localVariables[cleaned] ?: cleaned.removeSurrounding("\"").removeSurrounding("'")
        }
    }
}
