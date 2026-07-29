package com.devuloopers.knet.storage.apistudio

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room SQLite database entity representing a saved API request within a collection folder.
 *
 * @property id Unique request ID.
 * @property folderId Parent folder ID.
 * @property collectionId Parent collection ID.
 * @property name Display name of the endpoint (e.g. "/v1/auth/login").
 * @property method Primary HTTP method name (e.g. "GET", "POST", "CUSTOM").
 * @property customMethod Arbitrary custom HTTP verb (e.g. "PURGE", "PROPFIND").
 * @property url Full endpoint URL (e.g. "https://api.github.com/v1/auth/login").
 * @property headersJson Serialized JSON map of request headers.
 * @property bodyType Body payload mode ("JSON", "XML", "FORM_URLENCODED", "MULTIPART", "GRAPHQL", "RAW_TEXT").
 * @property bodyContent Raw body content string.
 * @property authType Auth mode ("NONE", "BEARER_TOKEN", "BASIC_AUTH", "API_KEY").
 * @property authToken Bearer token or secret string.
 * @property expectedStatus Expected HTTP status code for automated tests.
 */
@Entity(tableName = "saved_requests")
data class SavedRequestEntity(
    @PrimaryKey val id: String,
    val folderId: String,
    val collectionId: String,
    val name: String,
    val method: String,
    val customMethod: String? = null,
    val url: String,
    val headersJson: String = "{}",
    val bodyType: String = "NONE",
    val bodyContent: String = "",
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
