package com.devuloopers.knet.domain.apistudio.model

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
    val body: String = "",
    val bodyType: String = "json",
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
