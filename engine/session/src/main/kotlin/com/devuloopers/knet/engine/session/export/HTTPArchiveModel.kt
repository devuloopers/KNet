package com.devuloopers.knet.engine.session.export

import kotlinx.serialization.Serializable

@Serializable
data class HarLogRoot(
    val log: HarLog
)

@Serializable
data class HarLog(
    val version: String = "1.2",
    val creator: HarCreator = HarCreator(),
    val entries: List<HarEntry>
)

@Serializable
data class HarCreator(
    val name: String = "KNet Proxy",
    val version: String = "1.0.0"
)

@Serializable
data class HarEntry(
    val startedDateTime: String,
    val time: Double,
    val request: HarRequest,
    val response: HarResponse?,
    val cache: HarCache = HarCache(),
    val timings: HarTimings
)

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

@Serializable
data class HarHeader(
    val name: String,
    val value: String
)

@Serializable
data class HarQueryParam(
    val name: String,
    val value: String
)

@Serializable
data class HarCookie(
    val name: String,
    val value: String
)

@Serializable
data class HarContent(
    val size: Int,
    val mimeType: String,
    val text: String
)

@Serializable
data class HarPostData(
    val mimeType: String,
    val text: String
)

@Serializable
class HarCache

@Serializable
data class HarTimings(
    val send: Int = 0,
    val wait: Long,
    val receive: Int = 0
)
