package com.devuloopers.knet.application.usecase.apistudio

import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolAuthoringRegistry
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolDocument
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolDraft
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolOperation
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolSchemaImport
import com.devuloopers.knet.domain.request.descriptor.RequestKindId

public class ImportApiStudioProtocolSchemaUseCase(
    private val registry: ApiStudioProtocolAuthoringRegistry,
) {
    public fun execute(
        kind: RequestKindId,
        sourceId: String,
        bytes: ByteArray,
    ): Result<ApiStudioProtocolSchemaImport> = registry.importSchema(kind, sourceId, bytes)
}

public class ListApiStudioProtocolOperationsUseCase(
    private val registry: ApiStudioProtocolAuthoringRegistry,
) {
    public fun execute(kind: RequestKindId): List<ApiStudioProtocolOperation> = registry.operations(kind)
}

public class CreateApiStudioProtocolDocumentUseCase(
    private val registry: ApiStudioProtocolAuthoringRegistry,
) {
    public fun execute(
        kind: RequestKindId,
        draft: ApiStudioProtocolDraft,
    ): Result<ApiStudioProtocolDocument> = registry.createDocument(kind, draft)
}

public class ReadApiStudioProtocolDocumentUseCase(
    private val registry: ApiStudioProtocolAuthoringRegistry,
) {
    public fun execute(document: ApiStudioProtocolDocument): Result<ApiStudioProtocolDraft> =
        registry.readDocument(document)
}
