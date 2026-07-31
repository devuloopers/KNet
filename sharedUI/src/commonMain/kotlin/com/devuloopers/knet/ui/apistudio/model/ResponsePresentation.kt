package com.devuloopers.knet.ui.apistudio.model

import com.devuloopers.knet.bodyformatter.model.BodyFormat

/**
 * Immutable UI model representing a single cookie parsed from HTTP Set-Cookie response headers.
 *
 * @property name Cookie key name.
 * @property value Cookie value.
 * @property domain Domain scope.
 * @property path Path scope.
 * @property isSecure True if Secure flag is set.
 * @property isHttpOnly True if HttpOnly flag is set.
 */
data class ResponseCookieItem(
    val name: String,
    val value: String,
    val domain: String = "",
    val path: String = "",
    val isSecure: Boolean = false,
    val isHttpOnly: Boolean = false
)

/**
 * Universal immutable presentation model representing pre-computed HTTP response display data.
 * Built entirely on [kotlinx.coroutines.Dispatchers.Default] before UI state emission to ensure
 * Compose Multiplatform render frames perform 0ms CPU work.
 *
 * @property rawBody Original raw response payload string.
 * @property formattedBody Pretty-printed response payload string (JSON, XML, HTML, etc.).
 * @property bodyFormat Resolved strongly-typed [BodyFormat].
 * @property cookies Pre-parsed list of [ResponseCookieItem] extracted from headers.
 * @property lineCount Total line count of the formatted body payload.
 * @property characterCount Character length of the formatted body payload.
 */
data class ResponsePresentation(
    val rawBody: String,
    val formattedBody: String,
    val bodyFormat: BodyFormat,
    val cookies: List<ResponseCookieItem>,
    val lineCount: Int,
    val characterCount: Int
)
