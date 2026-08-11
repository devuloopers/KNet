package com.devuloopers.knet.domain.network.model

import com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType
import com.devuloopers.knet.domain.collection.model.ApiRequestAuth
import com.devuloopers.knet.domain.collection.model.HttpMethod

/**
 * Strongly-typed domain contract representing an HTTP request specification across KNet.
 *
 * Owned centrally by `:core:domain` to serve as the unified policy payload between
 * Traffic Capture, Live Interception, API Studio, cURL Importers, and future workspace tabs.
 *
 * @property method Strongly-typed HTTP method enum (GET, POST, PUT, DELETE, etc.).
 * @property customMethod Optional custom HTTP method string if [method] is [HttpMethod.CUSTOM].
 * @property url Complete target request URL string.
 * @property headers Preserved list of HTTP request header key-value pairs.
 * @property queryParams List of parsed request query parameter key-value pairs.
 * @property cookies List of parsed cookie key-value pairs.
 * @property bodyPayload Raw text payload string of the request body.
 * @property bodyType Strongly-typed body payload mode (NONE, JSON, XML, FORM_DATA, etc.).
 * @property auth Strongly-typed polymorphic authentication state.
 * @property timestamp Epoch timestamp in milliseconds when request was created or captured.
 */
data class NetworkRequestSpec(
    val method: HttpMethod = HttpMethod.GET,
    val customMethod: String? = null,
    val url: String = "",
    val headers: List<Pair<String, String>> = emptyList(),
    val queryParams: List<Pair<String, String>> = emptyList(),
    val cookies: List<Pair<String, String>> = emptyList(),
    val bodyPayload: String = "",
    val bodyType: RequestBodyType = RequestBodyType.NONE,
    val auth: ApiRequestAuth = ApiRequestAuth.None,
    val timestamp: Long = 0L
) {
    /**
     * Display method label string (upper-cased custom method if [HttpMethod.CUSTOM], else enum name).
     */
    val methodString: String
        get() = if (method == HttpMethod.CUSTOM && !customMethod.isNullOrBlank()) customMethod.uppercase() else method.name
}
