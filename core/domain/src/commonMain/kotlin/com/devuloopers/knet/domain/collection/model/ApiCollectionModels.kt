package com.devuloopers.knet.domain.collection.model

import com.devuloopers.knet.scripting.model.ScriptAssertion
import com.devuloopers.knet.scripting.model.ScriptLanguage
import com.devuloopers.knet.domain.validation.UrlValidator
import com.devuloopers.knet.traffic.model.http.HttpMethod

/**
 * Represents a single HTTP request header row.
 */
data class RequestHeader(
    val key: String,
    val value: String,
    val isEnabled: Boolean = true,
    val isAuto: Boolean = false
)

/**
 * Groups properties related to request body configuration.
 */
data class ApiRequestBody(
    val content: String = "",
    val type: String = "json" // e.g. "none", "json", "form-data", "raw-text"
)

/**
 * Groups properties related to pre-request and test scripting.
 */
data class ApiRequestScripts(
    val preRequest: String = "",
    val test: String = "",
    val language: ScriptLanguage = ScriptLanguage.JAVASCRIPT
)

/**
 * Represents the polymorphic authentication configuration for an API request.
 */
sealed interface ApiRequestAuth {
    data object None : ApiRequestAuth
    data object Inherit : ApiRequestAuth
    data class Bearer(val token: String) : ApiRequestAuth
    data class Basic(val username: String, val password: String) : ApiRequestAuth
    data class ApiKey(
        val name: String = "X-API-Key",
        val value: String = "",
        val location: String = "Header" // Header or Query Params
    ) : ApiRequestAuth
    data class OAuth2(
        val token: String = "",
        val headerPrefix: String = "Bearer"
    ) : ApiRequestAuth
    data class AwsSignature(
        val accessKey: String = "",
        val secretKey: String = "",
        val region: String = "us-east-1",
        val service: String = "s3"
    ) : ApiRequestAuth

    val type: String
        get() = when (this) {
            is None -> "No Auth"
            is Inherit -> "Inherit Auth"
            is Bearer -> "Bearer Token"
            is Basic -> "Basic Auth"
            is ApiKey -> "API Key"
            is OAuth2 -> "OAuth 2.0"
            is AwsSignature -> "AWS Signature"
        }
}

/**
 * Data model representing a saved request within a collection folder.
 */
data class SavedApiRequest(
    val id: String,
    val name: String,
    val method: HttpMethod,
    val url: String,
    val headers: List<RequestHeader> = defaultHeaders(),
    val body: ApiRequestBody = ApiRequestBody(),
    val auth: ApiRequestAuth = ApiRequestAuth.None,
    val scripts: ApiRequestScripts = ApiRequestScripts(),
    val expectedStatus: Int = 200,
    val testResults: List<ScriptAssertion> = emptyList()
) {
    val methodString: String
        get() = method.token
}

/**
 * Returns the 6 universal HTTP client default headers pre-seeded for every new [SavedApiRequest].
 */
fun defaultHeaders(): List<RequestHeader> = listOf(
    RequestHeader(key = "User-Agent",      value = "KNet-Desktop/2.4.0",   isEnabled = true,  isAuto = true),
    RequestHeader(key = "Accept",          value = "*/*",                  isEnabled = true,  isAuto = true),
    RequestHeader(key = "Accept-Encoding", value = "gzip, deflate, br",    isEnabled = true,  isAuto = true),
    RequestHeader(key = "Connection",      value = "keep-alive",           isEnabled = true,  isAuto = true),
    RequestHeader(key = "Host",            value = "",                     isEnabled = true,  isAuto = true),
    RequestHeader(key = "KNet-Token",      value = "",                     isEnabled = true,  isAuto = true)
)

/**
 * Data model representing a folder inside an API collection.
 */
data class CollectionFolder(
    val id: String,
    val name: String,
    val isExpanded: Boolean = true,
    val requests: List<SavedApiRequest> = emptyList()
)

/**
 * Data model representing an API collection suite.
 */
data class ApiCollection(
    val id: String,
    val name: String,
    val folders: List<CollectionFolder> = emptyList()
)

/**
 * Evaluates whether the provided API request URL is non-blank and structurally valid.
 */
fun isValidApiUrl(url: String): Boolean = UrlValidator.isValid(url)

/**
 * Extension property evaluating URL validity directly on a [SavedApiRequest].
 */
val SavedApiRequest.isUrlValid: Boolean
    get() = isValidApiUrl(url)
