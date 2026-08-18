package com.devuloopers.knet.storage.apistudio.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room SQLite database entity representing a folder inside an API collection.
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
