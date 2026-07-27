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
 * Data model for a test assertion result.
 */
data class TestAssertionResult(
    val id: String,
    val name: String,
    val passed: Boolean
)

/**
 * Data model representing a saved request within a collection folder.
 */
data class SavedApiRequest(
    val id: String,
    val name: String,
    val method: HttpMethod,
    val customMethod: String? = null,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
    val expectedStatus: Int = 200,
    val testResults: List<TestAssertionResult> = emptyList()
) {
    val methodString: String
        get() = if (method == HttpMethod.CUSTOM && !customMethod.isNullOrBlank()) customMethod.uppercase() else method.name
}

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
