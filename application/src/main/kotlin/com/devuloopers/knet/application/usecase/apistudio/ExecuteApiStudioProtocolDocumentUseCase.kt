package com.devuloopers.knet.application.usecase.apistudio

import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolExecutionCommand
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolExecutionEvent
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolExecutorRegistry
import kotlinx.coroutines.flow.Flow

/** Dispatches an opaque protocol document to its independently registered native executor. */
public class ExecuteApiStudioProtocolDocumentUseCase(
    private val registry: ApiStudioProtocolExecutorRegistry,
) {
    public fun execute(command: ApiStudioProtocolExecutionCommand): Flow<ApiStudioProtocolExecutionEvent> =
        registry.execute(command)
}
