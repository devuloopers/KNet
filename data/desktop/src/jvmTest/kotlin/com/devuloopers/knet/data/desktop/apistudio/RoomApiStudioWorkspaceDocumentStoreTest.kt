package com.devuloopers.knet.data.desktop.apistudio

import com.devuloopers.knet.application.port.apistudio.ApiStudioDocumentLocation
import com.devuloopers.knet.application.port.apistudio.ApiStudioEditorId
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolSchemaSource
import com.devuloopers.knet.application.port.apistudio.ApiStudioWorkspaceContent
import com.devuloopers.knet.application.port.apistudio.ApiStudioWorkspaceDocument
import com.devuloopers.knet.domain.apistudio.naming.RequestNameOrigin
import com.devuloopers.knet.domain.collection.model.ApiCollection
import com.devuloopers.knet.domain.collection.model.CollectionFolder
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.storage.database.DatabaseFactory
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class RoomApiStudioWorkspaceDocumentStoreTest {
    @Test
    fun `incomplete workspace document and schema survive database restart`() = runTest {
        val root = Files.createTempDirectory("knet-workspace-document-").toFile()
        val databaseFile = root.resolve("knet.db")
        try {
            val firstDatabase = DatabaseFactory.create(databaseFile)
            RoomApiStudioProtocolSchemaStore(firstDatabase.protocolDocumentDao(), nowMillis = { 41L })
                .saveSchema(ApiStudioProtocolSchemaSource(RequestKindId.GRPC, "lab.protoset", byteArrayOf(1, 2, 3)))
            RoomApiStudioWorkspaceDocumentStore(firstDatabase.protocolDocumentDao(), nowMillis = { 42L })
                .createDocument(workspaceDocument(payload = "incomplete-draft".encodeToByteArray()))
            firstDatabase.close()

            val restartedDatabase = DatabaseFactory.create(databaseFile)
            try {
                val document = assertNotNull(
                    RoomApiStudioWorkspaceDocumentStore(restartedDatabase.protocolDocumentDao()).document("grpc-1"),
                )
                val schema = assertNotNull(
                    RoomApiStudioProtocolSchemaStore(restartedDatabase.protocolDocumentDao())
                        .schema(RequestKindId.GRPC, "lab.protoset"),
                )

                assertEquals("Untitled gRPC Request", document.name)
                assertIs<ApiStudioDocumentLocation.Unsaved>(document.location)
                assertContentEquals("incomplete-draft".encodeToByteArray(), document.copyPayload())
                assertContentEquals(byteArrayOf(1, 2, 3), schema.copyPayload())
            } finally {
                restartedDatabase.close()
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `generated names follow content while user names survive promotion`() = runTest {
        val root = Files.createTempDirectory("knet-workspace-promotion-").toFile()
        val database = DatabaseFactory.create(root.resolve("knet.db"))
        try {
            var now = 1L
            val store = RoomApiStudioWorkspaceDocumentStore(database.protocolDocumentDao()) { now++ }
            store.createDocument(workspaceDocument())
            assertFailsWith<IllegalStateException> {
                store.updateContent(
                    "grpc-1",
                    workspaceContent("Wrong editor").copyWithEditor(ApiStudioEditorId.HTTP),
                )
            }
            store.updateContent("grpc-1", workspaceContent("Lab/UnaryEcho"))
            assertEquals("Lab/UnaryEcho", store.document("grpc-1")?.name)

            store.renameDocument("grpc-1", "My echo check")
            store.updateContent("grpc-1", workspaceContent("Lab/StreamingEcho"))
            assertEquals("My echo check", store.document("grpc-1")?.name)

            store.promoteToNewCollection(
                id = "grpc-1",
                name = "My echo check",
                nameOrigin = RequestNameOrigin.USER_DEFINED,
                collection = ApiCollection("collection-1", "gRPC Lab"),
                folder = CollectionFolder("folder-1", "Requests"),
            )

            val location = assertIs<ApiStudioDocumentLocation.Collection>(store.document("grpc-1")?.location)
            assertEquals("collection-1", location.collectionId)
            assertEquals("folder-1", location.folderId)
            assertNotNull(database.collectionDao().getCollectionById("collection-1"))
            assertEquals("folder-1", database.collectionDao().getFoldersForCollection("collection-1").single().id)
        } finally {
            database.close()
            root.deleteRecursively()
        }
    }
}

private fun workspaceDocument(payload: ByteArray = byteArrayOf()): ApiStudioWorkspaceDocument =
    ApiStudioWorkspaceDocument(
        id = "grpc-1",
        editorId = ApiStudioEditorId.GRPC,
        requestKind = RequestKindId.GRPC,
        name = "Untitled gRPC Request",
        nameOrigin = RequestNameOrigin.GENERATED,
        badgeLabel = "gRPC",
        payloadVersion = 1,
        payload = payload,
        location = ApiStudioDocumentLocation.Unsaved,
    )

private fun workspaceContent(name: String): ApiStudioWorkspaceContent = ApiStudioWorkspaceContent(
    editorId = ApiStudioEditorId.GRPC,
    requestKind = RequestKindId.GRPC,
    suggestedName = name,
    badgeLabel = "gRPC",
    payloadVersion = 1,
    payload = name.encodeToByteArray(),
)

private fun ApiStudioWorkspaceContent.copyWithEditor(editorId: ApiStudioEditorId): ApiStudioWorkspaceContent =
    ApiStudioWorkspaceContent(
        editorId = editorId,
        requestKind = requestKind,
        suggestedName = suggestedName,
        badgeLabel = badgeLabel,
        payloadVersion = payloadVersion,
        payload = copyPayload(),
    )
