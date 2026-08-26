package com.devuloopers.knet.application.usecase.apistudio

import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolExecutionCommand
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolExecutionSession
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolSessionExecutorRegistry

public class OpenApiStudioProtocolSessionUseCase(
    private val registry: ApiStudioProtocolSessionExecutorRegistry,
) {
    public fun execute(
        command: ApiStudioProtocolExecutionCommand,
    ): Result<ApiStudioProtocolExecutionSession> = registry.open(command)
}
