package com.devuloopers.knet.application.usecase.apistudio

import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolReflectionRegistry
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolReflectionResult
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolReflectionTarget
import com.devuloopers.knet.domain.request.descriptor.RequestKindId

public class ReflectApiStudioProtocolSchemaUseCase(
    private val registry: ApiStudioProtocolReflectionRegistry,
) {
    public suspend fun execute(
        kind: RequestKindId,
        target: ApiStudioProtocolReflectionTarget,
    ): Result<ApiStudioProtocolReflectionResult> = registry.reflect(kind, target)
}
