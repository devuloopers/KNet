package com.devuloopers.knet.storage.apistudio.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room SQLite database entity representing an API collection suite.
 */
@Entity(tableName = "api_collections")
public data class CollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
