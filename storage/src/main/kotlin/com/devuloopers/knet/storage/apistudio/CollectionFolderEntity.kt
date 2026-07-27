package com.devuloopers.knet.storage.apistudio

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room SQLite database entity representing a folder inside an API collection.
 *
 * @property id Unique folder ID.
 * @property collectionId Parent collection ID.
 * @property parentId Optional parent folder ID for nested sub-folders.
 * @property name Display name of the folder (e.g. "Auth", "Payments").
 * @property isExpanded UI state boolean indicating if folder is expanded in tree view.
 * @property orderIndex Sort order position.
 */
@Entity(tableName = "collection_folders")
data class CollectionFolderEntity(
    @PrimaryKey val id: String,
    val collectionId: String,
    val parentId: String? = null,
    val name: String,
    val isExpanded: Boolean = true,
    val orderIndex: Int = 0
)
