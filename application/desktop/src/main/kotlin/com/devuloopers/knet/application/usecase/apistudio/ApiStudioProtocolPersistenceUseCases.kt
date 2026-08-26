package com.devuloopers.knet.application.usecase.apistudio

import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolSchemaStore
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolSchemaSource
import com.devuloopers.knet.domain.request.descriptor.RequestKindId

public class SaveApiStudioProtocolSchemaUseCase(
    private val store: ApiStudioProtocolSchemaStore,
) {
    public suspend fun execute(source: ApiStudioProtocolSchemaSource): Unit = store.saveSchema(source)
}

public class LoadApiStudioProtocolSchemaUseCase(
    private val store: ApiStudioProtocolSchemaStore,
) {
    public suspend fun execute(kind: RequestKindId, sourceId: String): ApiStudioProtocolSchemaSource? =
        store.schema(kind, sourceId)
}
