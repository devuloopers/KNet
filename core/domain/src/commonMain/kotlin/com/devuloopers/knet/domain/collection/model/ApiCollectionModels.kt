package com.devuloopers.knet.domain.collection.model

import com.devuloopers.knet.domain.apistudio.naming.RequestNameOrigin
import com.devuloopers.knet.domain.clientNetwork.model.RawBodyFormat
import com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType
import com.devuloopers.knet.domain.clientNetwork.model.HttpVersionPreference
import com.devuloopers.knet.domain.validation.UrlValidator
import com.devuloopers.knet.scripting.model.ScriptAssertion
import com.devuloopers.knet.scripting.model.ScriptLanguage
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
 * Represents one persisted query-parameter row authored in API Studio.
 *
 * Disabled rows are retained separately from the URL so toggling a row does not destroy authored data.
 *
 * @property name Parameter name exactly as entered by the user.
 * @property value Parameter value exactly as entered by the user.
 * @property isEnabled Whether the parameter participates in request execution and URL rendering.
 */
data class RequestQueryParameter(
    val name: String,
    val value: String,
    val isEnabled: Boolean = true
)

/**
 * Represents one persisted request cookie authored in API Studio.
 *
 * @property name Cookie name exactly as entered by the user.
 * @property value Cookie value exactly as entered by the user.
 * @property isEnabled Whether the cookie participates in request execution.
 */
data class RequestCookie(
    val name: String,
    val value: String,
    val isEnabled: Boolean = true
)

/**
 * Represents one persisted structured body field used by form-data and URL-encoded editors.
 *
 * @property id Stable presentation identifier retained across save and restore operations.
 * @property key Field name exactly as authored.
 * @property value Field value exactly as authored.
 * @property isEnabled Whether the field participates in request execution.
 */
data class ApiRequestBodyField(
    val id: String,
    val key: String,
    val value: String,
    val isEnabled: Boolean = true
)

/**
 * Groups properties related to request body configuration.
 */
data class ApiRequestBody(
    val content: String = "",
    val type: RequestBodyType = RequestBodyType.NONE,
    val rawFormat: RawBodyFormat = RawBodyFormat.TEXT,
    val formDataFields: List<ApiRequestBodyField> = emptyList(),
    val urlEncodedFields: List<ApiRequestBodyField> = emptyList()
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
    val nameOrigin: RequestNameOrigin = RequestNameOrigin.USER_DEFINED,
    val method: HttpMethod,
    val httpVersionPreference: HttpVersionPreference = HttpVersionPreference.AUTO,
    val url: String,
    val queryParameters: List<RequestQueryParameter> = emptyList(),
    val headers: List<RequestHeader> = defaultHeaders(),
    val cookies: List<RequestCookie> = emptyList(),
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
    RequestHeader(key = "User-Agent", value = "KNet-Desktop/2.4.0", isEnabled = true, isAuto = true),
    RequestHeader(key = "Accept", value = "*/*", isEnabled = true, isAuto = true),
    RequestHeader(key = "Accept-Encoding", value = "gzip, deflate, br", isEnabled = true, isAuto = true),
    RequestHeader(key = "Connection", value = "keep-alive", isEnabled = true, isAuto = true),
    RequestHeader(key = "Host", value = "", isEnabled = true, isAuto = true),
    RequestHeader(key = "KNet-Token", value = "", isEnabled = true, isAuto = true)
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
