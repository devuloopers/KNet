package com.devuloopers.knet.data.desktop.inspection

import com.devuloopers.knet.application.contract.inspection.InspectionAnnotationStore
import com.devuloopers.knet.storage.capture.dao.CanonicalCaptureDao
import com.devuloopers.knet.storage.capture.entity.InspectionAnnotationEntity
import com.devuloopers.knet.traffic.id.CaptureSessionId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.inspection.InspectionAnnotation
import com.devuloopers.knet.traffic.inspection.InspectionAnnotationState
import com.devuloopers.knet.traffic.inspection.InspectionDocument
import com.devuloopers.knet.traffic.inspection.InspectionField
import com.devuloopers.knet.traffic.inspection.InspectorId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Room adapter for bounded, versioned generic semantic annotations. */
class RoomInspectionAnnotationAdapter(
    private val dao: CanonicalCaptureDao,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : InspectionAnnotationStore {
    override suspend fun put(sessionId: CaptureSessionId, annotation: InspectionAnnotation) {
        dao.upsertInspectionAnnotation(annotation.toEntity(sessionId))
    }

    override suspend fun get(exchangeId: ExchangeId): List<InspectionAnnotation> =
        dao.getInspectionAnnotations(exchangeId.value).mapNotNull(::toModel)

    override fun observe(exchangeId: ExchangeId): Flow<List<InspectionAnnotation>> =
        dao.observeInspectionAnnotations(exchangeId.value).map { entities -> entities.mapNotNull(::toModel) }

    override fun observe(
        exchangeIds: Set<ExchangeId>,
    ): Flow<Map<ExchangeId, List<InspectionAnnotation>>> {
        require(exchangeIds.isNotEmpty()) { "At least one exchange ID is required for batch observation." }
        return dao.observeInspectionAnnotations(exchangeIds.map(ExchangeId::value)).map { entities ->
            entities.mapNotNull(::toModel).groupBy(InspectionAnnotation::exchangeId)
        }
    }

    private fun InspectionAnnotation.toEntity(sessionId: CaptureSessionId): InspectionAnnotationEntity =
        InspectionAnnotationEntity(
            id = "${exchangeId.value}|${inspectorId.value}|$schemaVersion",
            sessionId = sessionId.value,
            exchangeId = exchangeId.value,
            inspectorId = inspectorId.value,
            version = schemaVersion,
            state = state.name,
            payloadEncoded = document?.encode(),
            errorCode = errorCode,
            createdAtEpochMillis = createdAtEpochMillis,
        )

    private fun toModel(entity: InspectionAnnotationEntity): InspectionAnnotation? = runCatching {
        InspectionAnnotation(
            exchangeId = ExchangeId(entity.exchangeId),
            inspectorId = InspectorId(entity.inspectorId),
            schemaVersion = entity.version,
            state = InspectionAnnotationState.valueOf(entity.state),
            document = entity.payloadEncoded?.let(::decodeDocument),
            errorCode = entity.errorCode,
            createdAtEpochMillis = entity.createdAtEpochMillis,
        )
    }.getOrNull()

    private fun InspectionDocument.encode(): String = buildJsonObject {
        put("kind", JsonPrimitive(kind))
        put("title", JsonPrimitive(title))
        summary?.let { put("summary", JsonPrimitive(it)) }
        put("fields", buildJsonArray {
            fields.forEach { field ->
                add(buildJsonObject {
                    put("label", JsonPrimitive(field.label))
                    put("value", JsonPrimitive(field.value))
                })
            }
        })
    }.toString()

    private fun decodeDocument(encoded: String): InspectionDocument {
        val root = json.parseToJsonElement(encoded).jsonObject
        return InspectionDocument(
            kind = root.requiredText("kind"),
            title = root.requiredText("title"),
            summary = root["summary"]?.jsonPrimitive?.content,
            fields = root["fields"]?.jsonArray.orEmpty().map { field ->
                val objectValue = field.jsonObject
                InspectionField(
                    label = objectValue.requiredText("label"),
                    value = objectValue.requiredText("value"),
                )
            },
        )
    }

    private fun JsonObject.requiredText(name: String): String =
        get(name)?.jsonPrimitive?.content?.takeIf(String::isNotBlank)
            ?: error("Inspection annotation field '$name' is missing.")
}

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
