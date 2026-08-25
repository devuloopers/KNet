package com.devuloopers.knet.ui.desktop.apistudio.grpc.persistence

import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolMetadataEntry
import com.devuloopers.knet.application.port.apistudio.ApiStudioDocumentLocation
import com.devuloopers.knet.application.port.apistudio.ApiStudioEditorId
import com.devuloopers.knet.application.port.apistudio.ApiStudioWorkspaceContent
import com.devuloopers.knet.application.port.apistudio.ApiStudioWorkspaceDocument
import com.devuloopers.knet.domain.apistudio.naming.RequestNameOrigin
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.ui.desktop.apistudio.grpc.model.GrpcStudioState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Versioned codec for incomplete gRPC editor state; strict execution validation happens later. */
class GrpcWorkspaceDraftCodec(
    private val json: Json = Json { ignoreUnknownKeys = false },
) {
    fun encode(state: GrpcStudioState): ByteArray = buildJsonObject {
        put("targetHost", state.targetHost)
        put("targetPort", state.targetPort)
        put("useTls", state.useTls)
        put("deadlineMillis", state.deadlineMillis)
        state.schemaSourceId?.let { put("schemaSourceId", it) }
        state.selectedOperation?.id?.let { put("selectedOperationId", it) }
        put("metadata", buildJsonArray {
            state.metadata.forEach { entry ->
                add(buildJsonObject {
                    put("name", entry.name)
                    put("value", entry.value)
                    put("enabled", entry.enabled)
                })
            }
        })
        put("outboundMessages", buildJsonArray {
            state.outboundMessages.forEach { add(JsonPrimitive(it)) }
        })
        put("selectedOutboundIndex", state.selectedOutboundIndex)
    }.toString().encodeToByteArray()

    fun decode(document: ApiStudioWorkspaceDocument): GrpcWorkspaceDraft {
        require(document.payloadVersion == PAYLOAD_VERSION) {
            "Unsupported gRPC workspace payload version ${document.payloadVersion}."
        }
        val root = json.parseToJsonElement(document.copyPayload().decodeToString()).jsonObject
        val messages = root.arrayOrEmpty("outboundMessages")
            .mapNotNull { it.jsonPrimitive.contentOrNull }
            .ifEmpty { listOf("") }
        return GrpcWorkspaceDraft(
            targetHost = root.stringOrEmpty("targetHost"),
            targetPort = root.stringOrEmpty("targetPort"),
            useTls = root["useTls"]?.jsonPrimitive?.booleanOrNull ?: false,
            deadlineMillis = root["deadlineMillis"]?.jsonPrimitive?.contentOrNull ?: "30000",
            schemaSourceId = root["schemaSourceId"]?.jsonPrimitive?.contentOrNull,
            selectedOperationId = root["selectedOperationId"]?.jsonPrimitive?.contentOrNull,
            metadata = root.arrayOrEmpty("metadata").map { element ->
                val entry = element.jsonObject
                ApiStudioProtocolMetadataEntry(
                    name = entry.stringOrEmpty("name"),
                    value = entry.stringOrEmpty("value"),
                    enabled = entry["enabled"]?.jsonPrimitive?.booleanOrNull ?: true,
                )
            },
            outboundMessages = messages,
            selectedOutboundIndex = (root["selectedOutboundIndex"]?.jsonPrimitive?.intOrNull ?: 0)
                .coerceIn(messages.indices),
        )
    }

    fun content(state: GrpcStudioState): ApiStudioWorkspaceContent = ApiStudioWorkspaceContent(
        editorId = ApiStudioEditorId.GRPC,
        requestKind = RequestKindId.GRPC,
        suggestedName = state.selectedOperation?.displayName ?: DEFAULT_DOCUMENT_NAME,
        badgeLabel = BADGE_LABEL,
        payloadVersion = PAYLOAD_VERSION,
        payload = encode(state),
    )

    /** Creates the durable unsaved document used when a transient editor receives its first authoring edit. */
    fun unsavedDocument(state: GrpcStudioState): ApiStudioWorkspaceDocument {
        require(state.documentId.isNotBlank()) { "A gRPC workspace document requires an ID." }
        val content = content(state)
        return ApiStudioWorkspaceDocument(
            id = state.documentId,
            editorId = content.editorId,
            requestKind = content.requestKind,
            name = content.suggestedName,
            nameOrigin = RequestNameOrigin.GENERATED,
            badgeLabel = content.badgeLabel,
            payloadVersion = content.payloadVersion,
            payload = content.copyPayload(),
            location = ApiStudioDocumentLocation.Unsaved,
        )
    }

    companion object {
        const val PAYLOAD_VERSION: Int = 1
        const val DEFAULT_DOCUMENT_NAME: String = "Untitled gRPC Request"
        const val BADGE_LABEL: String = "gRPC"
    }
}

data class GrpcWorkspaceDraft(
    val targetHost: String,
    val targetPort: String,
    val useTls: Boolean,
    val deadlineMillis: String,
    val schemaSourceId: String?,
    val selectedOperationId: String?,
    val metadata: List<ApiStudioProtocolMetadataEntry>,
    val outboundMessages: List<String>,
    val selectedOutboundIndex: Int,
)

private fun JsonObject.stringOrEmpty(name: String): String = this[name]?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject.arrayOrEmpty(name: String): JsonArray = this[name]?.jsonArray ?: JsonArray(emptyList())
