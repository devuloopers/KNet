package com.devuloopers.knet.application.port.apistudio

import com.devuloopers.knet.domain.apistudio.naming.RequestNameOrigin
import com.devuloopers.knet.domain.collection.model.ApiCollection
import com.devuloopers.knet.domain.collection.model.CollectionFolder
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import kotlinx.coroutines.flow.Flow

/** Open identifier for one API Studio editor implementation, independent from semantic request kind. */
@JvmInline
public value class ApiStudioEditorId(public val value: String) {
    init {
        require(value.isNotBlank()) { "API Studio editor ID must not be blank." }
        require(value == value.trim().lowercase()) {
            "API Studio editor ID must be a normalized lowercase token."
        }
    }

    public companion object {
        public val HTTP: ApiStudioEditorId = ApiStudioEditorId("http")
        public val GRPC: ApiStudioEditorId = ApiStudioEditorId("grpc")
    }
}

/** Durable placement shared by HTTP-independent API Studio documents. */
public sealed interface ApiStudioDocumentLocation {
    public data object Unsaved : ApiStudioDocumentLocation

    public data class Collection(
        public val collectionId: String,
        public val folderId: String,
    ) : ApiStudioDocumentLocation {
        init {
            require(collectionId.isNotBlank()) { "Collection ID must not be blank." }
            require(folderId.isNotBlank()) { "Collection folder ID must not be blank." }
        }
    }
}

/**
 * Opaque, versioned editor document that may contain an incomplete authoring state.
 *
 * The API Studio shell owns identity, placement, and presentation metadata. The contributing editor exclusively
 * owns [payload] and converts it into a strict [ApiStudioProtocolDocument] only when execution is requested.
 */
public class ApiStudioWorkspaceDocument(
    public val id: String,
    public val editorId: ApiStudioEditorId,
    public val requestKind: RequestKindId,
    public val name: String,
    public val nameOrigin: RequestNameOrigin,
    public val badgeLabel: String,
    public val payloadVersion: Int,
    payload: ByteArray,
    public val location: ApiStudioDocumentLocation,
) {
    private val encodedPayload: ByteArray = payload.copyOf()

    init {
        require(id.isNotBlank()) { "API Studio document ID must not be blank." }
        require(name.isNotBlank()) { "API Studio document name must not be blank." }
        require(badgeLabel.isNotBlank()) { "API Studio document badge must not be blank." }
        require(payloadVersion > 0) { "API Studio document payload version must be positive." }
        require(encodedPayload.size <= MAXIMUM_PAYLOAD_BYTES) { "API Studio document payload is too large." }
    }

    public fun copyPayload(): ByteArray = encodedPayload.copyOf()

    public companion object {
        public const val MAXIMUM_PAYLOAD_BYTES: Int = 16 * 1_024 * 1_024
    }
}

/** Updated opaque content emitted by an editor without permission to change document placement. */
public class ApiStudioWorkspaceContent(
    public val editorId: ApiStudioEditorId,
    public val requestKind: RequestKindId,
    public val suggestedName: String,
    public val badgeLabel: String,
    public val payloadVersion: Int,
    payload: ByteArray,
) {
    private val encodedPayload: ByteArray = payload.copyOf()

    init {
        require(suggestedName.isNotBlank()) { "API Studio suggested name must not be blank." }
        require(badgeLabel.isNotBlank()) { "API Studio document badge must not be blank." }
        require(payloadVersion > 0) { "API Studio content payload version must be positive." }
        require(encodedPayload.size <= ApiStudioWorkspaceDocument.MAXIMUM_PAYLOAD_BYTES) {
            "API Studio document payload is too large."
        }
    }

    public fun copyPayload(): ByteArray = encodedPayload.copyOf()
}

/** Protocol-neutral persistence boundary used by the common API Studio sidebar and contributed editors. */
public interface ApiStudioWorkspaceDocumentStore {
    public fun observeDocuments(): Flow<List<ApiStudioWorkspaceDocument>>

    public suspend fun document(id: String): ApiStudioWorkspaceDocument?

    public suspend fun createDocument(document: ApiStudioWorkspaceDocument)

    public suspend fun updateContent(id: String, content: ApiStudioWorkspaceContent)

    public suspend fun deleteDocument(id: String)

    public suspend fun renameDocument(id: String, name: String)

    public suspend fun promoteToExistingCollection(
        id: String,
        name: String,
        nameOrigin: RequestNameOrigin,
        collectionId: String,
        folderId: String,
    )

    public suspend fun promoteToNewCollection(
        id: String,
        name: String,
        nameOrigin: RequestNameOrigin,
        collection: ApiCollection,
        folder: CollectionFolder,
    )
}

/** Imported protocol assets remain independent from workspace-document placement. */
public interface ApiStudioProtocolSchemaStore {
    public suspend fun saveSchema(source: ApiStudioProtocolSchemaSource)

    public suspend fun schema(kind: RequestKindId, sourceId: String): ApiStudioProtocolSchemaSource?
}
