package com.devuloopers.knet.domain.apistudio.model

import com.devuloopers.knet.scriptengine.api.ScriptLanguage

/**
 * Supported HTTP methods for collection requests with exact Postman theme color coding.
 */
enum class HttpMethod(val badgeColorHex: Long) {
    GET(0xFF10B981),       // Vivid Emerald Green
    POST(0xFFF59E0B),      // Bright Amber Yellow
    PUT(0xFF3B82F6),       // Electric Blue
    PATCH(0xFFA855F7),     // Rich Lavender Purple
    DELETE(0xFFF87171),    // Vivid Coral Red
    HEAD(0xFF34D399),      // Light Mint Green
    OPTIONS(0xFFEC4899),   // Vivid Magenta Pink
    CUSTOM(0xFF9CA3AF)     // Sleek Slate Grey
}

/**
 * Represents a single HTTP request header row.
 *
 * @property key The header name (e.g. "Content-Type").
 * @property value The header value (e.g. "application/json"). May be "<auto>" for calculated headers.
 * @property isEnabled Whether this header row is checked and will be sent with the request.
 * @property isAuto True if this header was seeded automatically by KNet (shows "Auto" badge in UI).
 *                  False if manually added by the user.
 */
data class RequestHeader(
    val key: String,
    val value: String,
    val isEnabled: Boolean = true,
    val isAuto: Boolean = false
)

/**
 * Data model for a test assertion result.
 */
data class TestAssertionResult(
    val id: String,
    val name: String,
    val passed: Boolean
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
 *
 * @property headers List of [RequestHeader] rows shown in the Headers tab.
 *                   Always pre-seeded with universal HTTP client defaults via [defaultHeaders].
 */
data class SavedApiRequest(
    val id: String,
    val name: String,
    val method: HttpMethod,
    val customMethod: String? = null,
    val url: String,
    val headers: List<RequestHeader> = defaultHeaders(),
    val body: ApiRequestBody = ApiRequestBody(),
    val auth: ApiRequestAuth = ApiRequestAuth.None,
    val scripts: ApiRequestScripts = ApiRequestScripts(),
    val expectedStatus: Int = 200,
    val testResults: List<TestAssertionResult> = emptyList()
) {
    val methodString: String
        get() = if (method == HttpMethod.CUSTOM && !customMethod.isNullOrBlank()) customMethod.uppercase() else method.name
}

/**
 * Returns the 6 universal HTTP client default headers pre-seeded for every new [SavedApiRequest].
 *
 * These are identical to what Postman auto-generates for every endpoint regardless of URL.
 * `Host` and `KNet-Token` are shown as `<auto>` since their values are calculated at send time.
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
 *
 * Supports `http://`, `https://`, `localhost`, IP targets (`127.0.0.1`),
 * environment variable syntax (`{{variableName}}`), and standard domain name patterns.
 *
 * @param url The target URL string to validate.
 * @return True if the URL is non-blank and valid; false otherwise.
 */
fun isValidApiUrl(url: String): Boolean {
    val trimmed = url.trim()
    if (trimmed.isBlank()) return false

    // Environment variable syntax e.g. {{baseUrl}}/endpoint
    if (trimmed.startsWith("{{") && trimmed.contains("}}")) return true

    // Direct localhost or IP targets
    if (trimmed.startsWith("localhost", ignoreCase = true) ||
        trimmed.startsWith("127.0.0.1") ||
        trimmed.startsWith("0.0.0.0") ||
        trimmed.startsWith("http://localhost", ignoreCase = true) ||
        trimmed.startsWith("https://localhost", ignoreCase = true)
    ) {
        return true
    }

    // Standard URL scheme check or dot notation domain check
    val hasValidScheme = trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)
    val hasDomainDot = trimmed.contains(".")

    return hasValidScheme || hasDomainDot
}

/**
 * Extension property evaluating URL validity directly on a [SavedApiRequest].
 */
val SavedApiRequest.isUrlValid: Boolean
    get() = isValidApiUrl(url)

