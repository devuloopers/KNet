package com.devuloopers.knet.storage.apistudio.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

/**
 * Room SQLite database entity representing a saved API request within a collection folder.
 */
@Entity(tableName = "saved_requests")
data class SavedRequestEntity(
    @PrimaryKey val id: String,
    val folderId: String,
    val collectionId: String,
    val name: String,
    @ColumnInfo(defaultValue = "'USER_DEFINED'")
    val nameOrigin: String = "USER_DEFINED",
    val method: String,
    val url: String,
    @ColumnInfo(defaultValue = "'[]'")
    val queryParamsJson: String = "[]",
    val headersJson: String = "[]",
    @ColumnInfo(defaultValue = "'[]'")
    val cookiesJson: String = "[]",
    val bodyType: String = "NONE",
    val bodyContent: String = "",
    @ColumnInfo(defaultValue = "'TEXT'")
    val bodyRawSubType: String = "TEXT",
    @ColumnInfo(defaultValue = "'[]'")
    val bodyFormDataJson: String = "[]",
    @ColumnInfo(defaultValue = "'[]'")
    val bodyUrlEncodedJson: String = "[]",
    val authType: String = "NONE",
    val authToken: String = "",
    val expectedStatus: Int = 200,
    val authUsername: String = "",
    val authPassword: String = "",
    val apiKeyName: String = "X-API-Key",
    val apiKeyValue: String = "",
    val apiKeyLocation: String = "Header",
    val oauthHeaderPrefix: String = "Bearer",
    val awsAccessKey: String = "",
    val awsSecretKey: String = "",
    val awsRegion: String = "us-east-1",
    val awsService: String = "s3",
    val preRequestScript: String = "",
    val testScript: String = "",
    val scriptLanguage: String = "JAVASCRIPT"
)
