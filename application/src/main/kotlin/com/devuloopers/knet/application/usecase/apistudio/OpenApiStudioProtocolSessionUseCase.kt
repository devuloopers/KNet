package com.devuloopers.knet.application.usecase.apistudio

import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolExecutionCommand
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolExecutionSession
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolSessionExecutorRegistry

public class OpenApiStudioProtocolSessionUseCase(
    private val registry: ApiStudioProtocolSessionExecutorRegistry,
) {
    public fun execute(
        command: ApiStudioProtocolExecutionCommand,
    ): Result<ApiStudioProtocolExecutionSession> = registry.open(command)
}
