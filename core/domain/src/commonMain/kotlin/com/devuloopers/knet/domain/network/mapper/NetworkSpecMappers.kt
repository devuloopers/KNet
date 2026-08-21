package com.devuloopers.knet.domain.network.mapper

import com.devuloopers.knet.domain.apistudio.naming.RequestNameOrigin
import com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType
import com.devuloopers.knet.domain.collection.model.ApiRequestBody
import com.devuloopers.knet.domain.collection.model.RequestHeader
import com.devuloopers.knet.domain.collection.model.RequestCookie
import com.devuloopers.knet.domain.collection.model.RequestQueryParameter
import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import com.devuloopers.knet.domain.network.model.NetworkRequestSpec
import com.devuloopers.knet.domain.util.UrlQueryStringParser

/**
 * Extension mappers providing zero-data-loss conversions between collection models and the
 * authored [NetworkRequestSpec] contract.
 */
object NetworkSpecMappers {

    /**
     * Converts a [SavedApiRequest] collection entity into a unified [NetworkRequestSpec].
     *
     * @return Formatted [NetworkRequestSpec].
     */
    fun SavedApiRequest.toNetworkRequestSpec(): NetworkRequestSpec {
        val headerPairs = this.headers.filter { it.isEnabled }.map { it.key to it.value }
        val queryParamsList = if (queryParameters.isNotEmpty()) {
            queryParameters.filter { it.isEnabled }.map { it.name to it.value }
        } else {
            UrlQueryStringParser.parseQueryParams(this.url)
        }
        val cookiesList = if (cookies.isNotEmpty()) {
            cookies.filter { it.isEnabled }.map { it.name to it.value }
        } else {
            extractCookiesFromHeaders(headerPairs)
        }

        return NetworkRequestSpec(
            method = this.method,
            httpVersionPreference = this.httpVersionPreference,
            url = this.url,
            headers = headerPairs,
            queryParams = queryParamsList,
            cookies = cookiesList,
            bodyPayload = this.body.content,
            bodyType = this.body.type,
            auth = this.auth
        )
    }

    /**
     * Converts a unified [NetworkRequestSpec] into a domain [SavedApiRequest] entity.
     *
     * @param id Target request identifier string.
     * @param name Target request title name string.
     * @param nameOrigin Whether the supplied title is generated or explicitly user-owned.
     * @return Formatted domain [SavedApiRequest] entity.
     */
    fun NetworkRequestSpec.toSavedApiRequest(
        id: String,
        name: String,
        nameOrigin: RequestNameOrigin = RequestNameOrigin.USER_DEFINED
    ): SavedApiRequest {
        val requestHeaders = this.headers.map { RequestHeader(key = it.first, value = it.second, isEnabled = true) }
        return SavedApiRequest(
            id = id,
            name = name,
            nameOrigin = nameOrigin,
            method = this.method,
            httpVersionPreference = this.httpVersionPreference,
            url = this.url,
            queryParameters = queryParams.map { (name, value) ->
                RequestQueryParameter(name = name, value = value)
            },
            headers = requestHeaders,
            cookies = cookies.map { RequestCookie(name = it.first, value = it.second) },
            body = ApiRequestBody(content = this.bodyPayload, type = this.bodyType),
            auth = this.auth
        )
    }

    private fun extractCookiesFromHeaders(headers: List<Pair<String, String>>): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        headers.forEach { (key, value) ->
            if (key.equals("Cookie", ignoreCase = true)) {
                value.split(";").forEach { cookiePart ->
                    val pair = cookiePart.trim().split("=", limit = 2)
                    if (pair.size == 2 && pair[0].isNotBlank()) {
                        result.add(pair[0].trim() to pair[1].trim())
                    }
                }
            } else if (key.equals("Set-Cookie", ignoreCase = true)) {
                val firstPart = value.split(";").firstOrNull()?.trim() ?: ""
                val pair = firstPart.split("=", limit = 2)
                if (pair.size == 2 && pair[0].isNotBlank()) {
                    result.add(pair[0].trim() to pair[1].trim())
                }
            }
        }
        return result
    }

    /**
     * Set of low-level HTTP transport headers managed automatically by TCP sockets and HTTP client engines.
     */
    private val restrictedTransportHeaders: Set<String> = setOf(
        "content-length",
        "host",
        "connection",
        "transfer-encoding"
    )

    /**
     * Sanitizes a list of header key-value pairs by removing transport-managed headers.
     */
    fun List<Pair<String, String>>.sanitizeTransportHeaders(): List<Pair<String, String>> {
        return this.filterNot { (key, _) -> key.trim().lowercase() in restrictedTransportHeaders }
    }

    /**
     * Sanitizes a header map by removing transport-managed headers.
     */
    fun Map<String, String>.sanitizeTransportHeaders(): Map<String, String> {
        return this.filterKeys { key -> key.trim().lowercase() !in restrictedTransportHeaders }
    }

}
