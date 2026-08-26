package com.devuloopers.knet.engine.grpc

import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolDocument
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/** gRPC call cardinality derived from the selected method descriptor. */
enum class GrpcCallShape {
    UNARY,
    SERVER_STREAMING,
    CLIENT_STREAMING,
    BIDIRECTIONAL_STREAMING,
}

/** Ordered gRPC metadata entry; binary values use unpadded Base64 text. */
data class GrpcMetadataEntry(
    val name: String,
    val value: String,
    val enabled: Boolean = true,
) {
    init {
        require(name.isNotBlank()) { "gRPC metadata name must not be blank." }
        require(name == name.lowercase()) { "gRPC metadata names must be lowercase." }
        require(!name.startsWith(':')) { "gRPC pseudo-headers cannot be authored as metadata." }
    }
}

/** Typed native gRPC draft owned by `:engine:grpc`, outside the ordinary HTTP model. */
data class GrpcRequestDraft(
    val targetHost: String,
    val targetPort: Int,
    val useTls: Boolean,
    val method: GrpcMethodIdentity,
    val callShape: GrpcCallShape,
    val deadlineMillis: Long = 30_000L,
    val metadata: List<GrpcMetadataEntry> = emptyList(),
    val outboundMessagesJson: List<String>,
    val descriptorSourceId: String? = null,
) {
    init {
        require(targetHost.isNotBlank()) { "gRPC target host must not be blank." }
        require(targetPort in 1..65_535) { "gRPC target port is invalid." }
        require(deadlineMillis in 1L..3_600_000L) { "gRPC deadline is invalid." }
        require(outboundMessagesJson.isNotEmpty()) { "A gRPC call requires at least one outbound message." }
        require(outboundMessagesJson.all { it.isNotBlank() }) { "A gRPC outbound message must not be blank." }
        require(outboundMessagesJson.size <= MAXIMUM_OUTBOUND_MESSAGES) { "Too many outbound gRPC messages." }
        require(outboundMessagesJson.sumOf { it.encodeToByteArray().size } <= MAXIMUM_DRAFT_MESSAGE_BYTES) {
            "Authored gRPC messages exceed the draft limit."
        }
    }

    companion object {
        const val MAXIMUM_OUTBOUND_MESSAGES: Int = 1_000
        const val MAXIMUM_DRAFT_MESSAGE_BYTES: Int = 16 * 1_024 * 1_024
    }
}

/** Versioned, strict codec used by persistence and the native executor. */
class GrpcRequestDraftCodec(
    private val json: Json = Json { ignoreUnknownKeys = false },
) {
    fun encode(id: String, name: String, draft: GrpcRequestDraft): ApiStudioProtocolDocument {
        val payload = buildJsonObject {
            put("targetHost", draft.targetHost)
            put("targetPort", draft.targetPort)
            put("useTls", draft.useTls)
            put("service", draft.method.serviceName)
            put("method", draft.method.methodName)
            put("callShape", draft.callShape.name)
            put("deadlineMillis", draft.deadlineMillis)
            draft.descriptorSourceId?.let { put("descriptorSourceId", it) }
            put("metadata", buildJsonArray {
                draft.metadata.forEach { entry ->
                    add(buildJsonObject {
                        put("name", entry.name)
                        put("value", entry.value)
                        put("enabled", entry.enabled)
                    })
                }
            })
            put("outboundMessagesJson", JsonArray(draft.outboundMessagesJson.map(::JsonPrimitive)))
        }
        return ApiStudioProtocolDocument(
            id = id,
            name = name,
            kind = RequestKindId.GRPC,
            schemaVersion = SCHEMA_VERSION,
            payload = json.encodeToString(JsonObject.serializer(), payload).encodeToByteArray(),
        )
    }

    fun decode(document: ApiStudioProtocolDocument): Result<GrpcRequestDraft> = runCatching {
        require(document.kind == RequestKindId.GRPC) { "Document is not a gRPC draft." }
        require(document.schemaVersion == SCHEMA_VERSION) { "Unsupported gRPC draft schema version." }
        val root = json.parseToJsonElement(document.copyPayload().decodeToString()).jsonObject
        GrpcRequestDraft(
            targetHost = root.requiredString("targetHost"),
            targetPort = root.getValue("targetPort").jsonPrimitive.int,
            useTls = root.getValue("useTls").jsonPrimitive.boolean,
            method = GrpcMethodIdentity(
                serviceName = root.requiredString("service"),
                methodName = root.requiredString("method"),
            ),
            callShape = GrpcCallShape.valueOf(root.requiredString("callShape")),
            deadlineMillis = root.getValue("deadlineMillis").jsonPrimitive.long,
            descriptorSourceId = root["descriptorSourceId"]?.jsonPrimitive?.contentOrNull,
            metadata = root.getValue("metadata").jsonArray.map { element ->
                val entry = element.jsonObject
                GrpcMetadataEntry(
                    name = entry.requiredString("name"),
                    value = entry.requiredString("value"),
                    enabled = entry.getValue("enabled").jsonPrimitive.boolean,
                )
            },
            outboundMessagesJson = root.getValue("outboundMessagesJson").jsonArray
                .map { it.jsonPrimitive.content },
        )
    }

    private fun JsonObject.requiredString(name: String): String =
        requireNotNull(get(name)?.jsonPrimitive?.contentOrNull) { "Missing gRPC draft field '$name'." }

    companion object {
        const val SCHEMA_VERSION: Int = 1
    }
}
