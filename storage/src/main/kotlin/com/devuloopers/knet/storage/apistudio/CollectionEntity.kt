package com.devuloopers.knet.storage.apistudio

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room SQLite database entity representing an API collection suite.
 *
 * @property id Unique collection ID.
 * @property name Display name of the collection (e.g. "Authentication APIs").
 * @property createdAt Creation epoch millisecond timestamp.
 * @property updatedAt Last modification epoch millisecond timestamp.
 */
@Entity(tableName = "api_collections")
data class CollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
