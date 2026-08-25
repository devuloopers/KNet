package com.devuloopers.knet.data.desktop.apistudio

import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolSchemaSource
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolSchemaStore
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.storage.apistudio.dao.ProtocolDocumentDao
import com.devuloopers.knet.storage.apistudio.entity.ApiStudioProtocolSchemaEntity
import kotlin.time.Clock

/** Room adapter for imported descriptors and future protocol-owned schema assets. */
class RoomApiStudioProtocolSchemaStore(
    private val dao: ProtocolDocumentDao,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ApiStudioProtocolSchemaStore {
    override suspend fun saveSchema(source: ApiStudioProtocolSchemaSource) {
        dao.upsertSchema(
            ApiStudioProtocolSchemaEntity(
                kind = source.kind.value,
                sourceId = source.sourceId,
                payload = source.copyPayload(),
                updatedAtEpochMillis = nowMillis(),
            ),
        )
    }

    override suspend fun schema(kind: RequestKindId, sourceId: String): ApiStudioProtocolSchemaSource? =
        dao.schema(kind.value, sourceId)?.let { entity ->
            ApiStudioProtocolSchemaSource(
                kind = RequestKindId(entity.kind),
                sourceId = entity.sourceId,
                payload = entity.payload,
            )
        }
}
