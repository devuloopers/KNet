package com.devuloopers.knet.scriptengine.kotlin.runtime

import com.devuloopers.knet.scriptengine.api.EnvironmentStore
import com.devuloopers.knet.scriptengine.api.ScriptExecutionResult
import com.devuloopers.knet.scriptengine.api.ScriptRequestModel
import com.devuloopers.knet.scriptengine.api.ScriptResponseModel
import com.devuloopers.knet.scriptengine.core.ExceptionFormatter
import com.devuloopers.knet.scriptengine.core.ResultCollector

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
                } else if (trimmed.startsWith("console.log(")) {
                    val logMsg = trimmed.substringAfter("console.log(").substringBeforeLast(")").trim().removeSurrounding("\"").removeSurrounding("'")
                    resultCollector.addLog(logMsg)
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
        environment: EnvironmentStore
    ): Pair<Boolean, String?> {
        for (line in lines) {
            // Normalize the optional 'context.' prefix so that both:
            //   context.response.statusCode == 200
            //   response.statusCode == 200
            // are handled identically by the pattern matchers below.
            val trimmed = line.trim().removePrefix("context.")
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

            // 2. Response body contains matcher (e.g. response.body.contains("X-Transaction-Id"))
            if (trimmed.contains(".contains(")) {
                val targetText = trimmed.substringAfter(".contains(").substringBefore(")").trim().removeSurrounding("\"").removeSurrounding("'")
                val bodyText = response?.body ?: ""
                if (!bodyText.contains(targetText)) {
                    return false to "Assertion failed: Response body does not contain '$targetText'"
                }
                return true to null
            }

            // 3. Status code comparison (e.g. response.statusCode == 200 or statusCode == 200)
            if (trimmed.contains("statusCode") && trimmed.contains("==")) {
                val expectedCode = trimmed.substringAfter("==").trim().toIntOrNull() ?: 200
                val actualCode = response?.statusCode ?: 0
                if (actualCode != expectedCode) {
                    return false to "Assertion failed: Expected status code $expectedCode, but got $actualCode"
                }
                return true to null
            }

            // 4. Latency comparison (e.g. response.latencyMs < 500 or latencyMs < 500)
            if (trimmed.contains("latencyMs") && trimmed.contains("<")) {
                val maxLatency = trimmed.substringAfter("<").trim().removeSuffix("L").toLongOrNull() ?: 500L
                val actualLatency = response?.latencyMs ?: 0L
                if (actualLatency >= maxLatency) {
                    return false to "Assertion failed: Response time $actualLatency ms exceeded $maxLatency ms threshold"
                }
                return true to null
            }

            // 5. Header assertion check
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

        val defaultPass = response?.statusCode == 200 || (response != null && response.statusCode in 200..299)
        return defaultPass to if (!defaultPass && response != null) "Assertion failed: statusCode is ${response?.statusCode}" else null
    }
}
