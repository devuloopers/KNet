package com.devuloopers.knet.session.httparchive

import kotlinx.serialization.Serializable

/**
 * Root HAR 1.2 log structure container.
 */
@Serializable
data class HarLogRoot(
    val log: HarLog
)

/**
 * HAR Log contents description block.
 */
@Serializable
data class HarLog(
    val version: String = "1.2",
    val creator: HarCreator = HarCreator(),
    val entries: List<HarEntry>
)

/**
 * Tool creator descriptor block.
 */
@Serializable
data class HarCreator(
    val name: String = "KNet Proxy",
    val version: String = "1.0.0"
)

/**
 * Represents a single HTTP transaction log entry.
 */
@Serializable
data class HarEntry(
    val startedDateTime: String,
    val time: Double,
    val request: HarRequest,
    val response: HarResponse?,
    val cache: HarCache = HarCache(),
    val timings: HarTimings
)

/**
 * Log descriptor for the HTTP request block.
 */
@Serializable
data class HarRequest(
    val method: String,
    val url: String,
    val httpVersion: String = "HTTP/1.1",
    val headers: List<HarHeader>,
    val queryString: List<HarQueryParam>,
    val cookies: List<HarCookie>,
    val headersSize: Int = -1,
    val bodySize: Int,
    val postData: HarPostData? = null
)

/**
 * Log descriptor for the HTTP response block.
 */
@Serializable
data class HarResponse(
    val status: Int,
    val statusText: String,
    val httpVersion: String = "HTTP/1.1",
    val headers: List<HarHeader>,
    val cookies: List<HarCookie>,
    val content: HarContent,
    val redirectURL: String = "",
    val headersSize: Int = -1,
    val bodySize: Int
)

/**
 * Key-value mapping representing HTTP Headers.
 */
@Serializable
data class HarHeader(
    val name: String,
    val value: String
)

/**
 * Key-value mapping representing HTTP query parameters.
 */
@Serializable
data class HarQueryParam(
    val name: String,
    val value: String
)

/**
 * Key-value mapping representing parsed cookies.
 */
@Serializable
data class HarCookie(
    val name: String,
    val value: String
)

/**
 * Represents HTTP response body content description block.
 */
@Serializable
data class HarContent(
    val size: Int,
    val mimeType: String,
    val text: String
)

/**
 * Represents POST request payload body content block.
 */
@Serializable
data class HarPostData(
    val mimeType: String,
    val text: String
)

/**
 * Cache descriptor block (defaults to empty).
 */
@Serializable
class HarCache

/**
 * Network timing metrics block.
 */
@Serializable
data class HarTimings(
    val send: Int = 0,
    val wait: Long,
    val receive: Int = 0
)
