package com.devuloopers.knet.application.usecase.apistudio

import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolReflectionRegistry
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolReflectionResult
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolReflectionTarget
import com.devuloopers.knet.domain.request.descriptor.RequestKindId

public class ReflectApiStudioProtocolSchemaUseCase(
    private val registry: ApiStudioProtocolReflectionRegistry,
) {
    public suspend fun execute(
        kind: RequestKindId,
        target: ApiStudioProtocolReflectionTarget,
    ): Result<ApiStudioProtocolReflectionResult> = registry.reflect(kind, target)
}
