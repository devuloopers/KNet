package com.devuloopers.knet.storage.apistudio.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Opaque editor document with protocol-neutral draft or collection placement. */
@Entity(
    tableName = "api_studio_workspace_documents",
    indices = [
        Index(value = ["editorId", "updatedAtEpochMillis"]),
        Index(value = ["collectionId", "folderId"]),
    ],
)
data class ApiStudioWorkspaceDocumentEntity(
    @PrimaryKey val id: String,
    val editorId: String,
    val requestKind: String,
    val name: String,
    val nameOrigin: String,
    val badgeLabel: String,
    val payloadVersion: Int,
    val payload: ByteArray,
    val collectionId: String?,
    val folderId: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ApiStudioWorkspaceDocumentEntity

        if (payloadVersion != other.payloadVersion) return false
        if (createdAtEpochMillis != other.createdAtEpochMillis) return false
        if (updatedAtEpochMillis != other.updatedAtEpochMillis) return false
        if (id != other.id) return false
        if (editorId != other.editorId) return false
        if (requestKind != other.requestKind) return false
        if (name != other.name) return false
        if (nameOrigin != other.nameOrigin) return false
        if (badgeLabel != other.badgeLabel) return false
        if (!payload.contentEquals(other.payload)) return false
        if (collectionId != other.collectionId) return false
        if (folderId != other.folderId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = payloadVersion
        result = 31 * result + createdAtEpochMillis.hashCode()
        result = 31 * result + updatedAtEpochMillis.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + editorId.hashCode()
        result = 31 * result + requestKind.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + nameOrigin.hashCode()
        result = 31 * result + badgeLabel.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + (collectionId?.hashCode() ?: 0)
        result = 31 * result + (folderId?.hashCode() ?: 0)
        return result
    }
}

/** Opaque imported schema persisted independently from a protocol document. */
@Entity(
    tableName = "api_studio_protocol_schemas",
    primaryKeys = ["kind", "sourceId"],
)
data class ApiStudioProtocolSchemaEntity(
    val kind: String,
    val sourceId: String,
    val payload: ByteArray,
    val updatedAtEpochMillis: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ApiStudioProtocolSchemaEntity

        if (updatedAtEpochMillis != other.updatedAtEpochMillis) return false
        if (kind != other.kind) return false
        if (sourceId != other.sourceId) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = updatedAtEpochMillis.hashCode()
        result = 31 * result + kind.hashCode()
        result = 31 * result + sourceId.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}
