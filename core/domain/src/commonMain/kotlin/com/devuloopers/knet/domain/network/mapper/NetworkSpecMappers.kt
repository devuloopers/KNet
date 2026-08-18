package com.devuloopers.knet.domain.network.mapper

import com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType
import com.devuloopers.knet.domain.collection.model.ApiRequestBody
import com.devuloopers.knet.domain.collection.model.RequestHeader
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
        val queryParamsList = UrlQueryStringParser.parseQueryParams(this.url)
        val cookiesList = extractCookiesFromHeaders(headerPairs)

        val parsedBodyType = when (this.body.type.lowercase()) {
            "json" -> RequestBodyType.JSON
            "xml" -> RequestBodyType.XML
            "form-data", "form" -> RequestBodyType.FORM_DATA
            "x-www-form-urlencoded" -> RequestBodyType.X_WWW_FORM_URLENCODED
            "multipart" -> RequestBodyType.MULTIPART
            "graphql" -> RequestBodyType.GRAPHQL
            "raw-text", "text" -> RequestBodyType.RAW_TEXT
            else -> RequestBodyType.NONE
        }

        return NetworkRequestSpec(
            method = this.method,
            url = this.url,
            headers = headerPairs,
            queryParams = queryParamsList,
            cookies = cookiesList,
            bodyPayload = this.body.content,
            bodyType = parsedBodyType,
            auth = this.auth
        )
    }

    /**
     * Converts a unified [NetworkRequestSpec] into a domain [SavedApiRequest] entity.
     *
     * @param id Target request identifier string.
     * @param name Target request title name string.
     * @return Formatted domain [SavedApiRequest] entity.
     */
    fun NetworkRequestSpec.toSavedApiRequest(id: String, name: String): SavedApiRequest {
        val requestHeaders = this.headers.map { RequestHeader(key = it.first, value = it.second, isEnabled = true) }
        val bodyTypeName = when (this.bodyType) {
            RequestBodyType.JSON -> "json"
            RequestBodyType.XML -> "xml"
            RequestBodyType.FORM_DATA -> "form-data"
            RequestBodyType.X_WWW_FORM_URLENCODED -> "x-www-form-urlencoded"
            RequestBodyType.MULTIPART -> "multipart"
            RequestBodyType.GRAPHQL -> "graphql"
            RequestBodyType.RAW_TEXT -> "raw-text"
            RequestBodyType.NONE -> "none"
        }

        return SavedApiRequest(
            id = id,
            name = name,
            method = this.method,
            url = this.url,
            headers = requestHeaders,
            body = ApiRequestBody(content = this.bodyPayload, type = bodyTypeName),
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

    /**
     * Converts a [RequestBodyType] enum into a UI-compatible body mode string.
     */
    fun RequestBodyType.toEditorBodyMode(): String = when (this) {
        RequestBodyType.JSON -> "JSON"
        RequestBodyType.XML -> "XML"
        RequestBodyType.FORM_DATA -> "FORM_DATA"
        RequestBodyType.X_WWW_FORM_URLENCODED -> "X_WWW_FORM_URLENCODED"
        RequestBodyType.MULTIPART -> "FORM_DATA"
        RequestBodyType.GRAPHQL -> "GRAPHQL"
        RequestBodyType.RAW_TEXT -> "RAW"
        RequestBodyType.NONE -> "NONE"
    }

    /**
     * Converts a string representation of body mode into a strongly-typed [RequestBodyType] enum.
     */
    fun String.toRequestBodyType(): RequestBodyType = when (this.uppercase().trim()) {
        "JSON" -> RequestBodyType.JSON
        "XML" -> RequestBodyType.XML
        "FORM", "FORM_DATA", "FORM-DATA" -> RequestBodyType.FORM_DATA
        "X-WWW-FORM-URLENCODED", "X_WWW_FORM_URLENCODED", "URLENCODED" -> RequestBodyType.X_WWW_FORM_URLENCODED
        "MULTIPART" -> RequestBodyType.MULTIPART
        "GRAPHQL" -> RequestBodyType.GRAPHQL
        "RAW", "RAW_TEXT", "TEXT" -> RequestBodyType.RAW_TEXT
        else -> RequestBodyType.NONE
    }

}
