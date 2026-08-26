package com.devuloopers.knet.data.desktop.script

import com.devuloopers.knet.application.contract.script.ScriptExecutionCommand
import com.devuloopers.knet.application.contract.script.ScriptExecutionOutcome
import com.devuloopers.knet.application.contract.script.ScriptExecutor
import com.devuloopers.knet.application.contract.script.ScriptRequest
import com.devuloopers.knet.engine.script.api.EnvironmentStore
import com.devuloopers.knet.engine.script.api.ScriptExecutionResult
import com.devuloopers.knet.engine.script.api.ScriptRequestModel
import com.devuloopers.knet.engine.script.api.ScriptResponseModel
import com.devuloopers.knet.engine.script.runtime.ScriptRuntime

/** Desktop adapter that contains all knowledge of the concrete script engine. */
class DesktopScriptExecutionAdapter : ScriptExecutor {
    override suspend fun execute(command: ScriptExecutionCommand): ScriptExecutionOutcome {
        val environment = EnvironmentStore(command.environment)
        val result = ScriptRuntime.execute(
            language = command.language,
            code = command.source,
            request = command.request.toEngine(),
            response = command.response?.let { response ->
                ScriptResponseModel(
                    statusCode = response.statusCode,
                    statusText = response.statusText,
                    latencyMs = response.latencyMillis,
                    responseSizeBytes = response.responseSizeBytes,
                    headers = response.headers,
                    body = response.body,
                )
            },
            environment = environment,
        )
        return when (result) {
            is ScriptExecutionResult.Error -> ScriptExecutionOutcome.Failure(result.message)
            is ScriptExecutionResult.Success -> ScriptExecutionOutcome.Success(
                request = result.request.toApplication(),
                assertions = result.testResults,
                environment = result.environmentUpdates,
                logs = result.logs,
            )
        }
    }

    private fun ScriptRequest.toEngine(): ScriptRequestModel = ScriptRequestModel(
        url = url,
        method = method,
        headers = headers.toMutableMap(),
        queryParams = queryParameters.toMutableMap(),
        body = body,
    )

    private fun ScriptRequestModel.toApplication(): ScriptRequest = ScriptRequest(
        url = url,
        method = method,
        headers = headers.toMap(),
        queryParameters = queryParams.toMap(),
        body = body,
    )
}
