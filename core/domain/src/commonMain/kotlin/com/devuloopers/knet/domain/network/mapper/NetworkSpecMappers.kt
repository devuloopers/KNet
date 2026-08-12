package com.devuloopers.knet.domain.network.mapper

import com.devuloopers.knet.domain.clientNetwork.model.HttpRequest
import com.devuloopers.knet.domain.clientNetwork.model.HttpResponse
import com.devuloopers.knet.domain.clientNetwork.model.HttpTransaction
import com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType
import com.devuloopers.knet.domain.collection.model.ApiRequestBody
import com.devuloopers.knet.domain.collection.model.HttpMethod
import com.devuloopers.knet.domain.collection.model.RequestHeader
import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import com.devuloopers.knet.domain.network.model.NetworkRequestSpec
import com.devuloopers.knet.domain.network.model.NetworkResponseSpec
import com.devuloopers.knet.domain.util.UrlQueryStringParser

/**
 * Extension mappers providing zero-data-loss conversions between domain models and unified [NetworkRequestSpec]/[NetworkResponseSpec] policy contracts.
 */
object NetworkSpecMappers {

    /**
     * Converts a raw captured [HttpRequest] domain model into a unified [NetworkRequestSpec].
     *
     * @param bodyString Optional explicit string representation of body payload.
     * @return Formatted [NetworkRequestSpec].
     */
    fun HttpRequest.toNetworkRequestSpec(bodyString: String? = null): NetworkRequestSpec {
        val parsedMethod = parseHttpMethod(this.method)
        val queryParamsList = UrlQueryStringParser.parseQueryParams(this.url)
        val cookiesList = extractCookiesFromHeaders(this.headers)
        val resolvedBody = bodyString ?: this.body?.decodeToString() ?: ""
        val detectedBodyType = inferRequestBodyType(resolvedBody, this.headers)

        return NetworkRequestSpec(
            method = parsedMethod.first,
            customMethod = parsedMethod.second,
            url = this.url,
            headers = this.headers.sanitizeTransportHeaders(),
            queryParams = queryParamsList,
            cookies = cookiesList,
            bodyPayload = resolvedBody,
            bodyType = detectedBodyType,
            timestamp = this.timestamp
        )
    }

    /**
     * Converts a raw captured [HttpResponse] domain model into a unified [NetworkResponseSpec].
     *
     * @param durationMs Execution latency in milliseconds.
     * @param sizeBytes Response body byte size.
     * @param bodyString Optional explicit string representation of body payload.
     * @return Formatted [NetworkResponseSpec].
     */
    fun HttpResponse.toNetworkResponseSpec(
        durationMs: Long = 0L,
        sizeBytes: Long = (this.body?.size ?: 0).toLong(),
        bodyString: String? = null
    ): NetworkResponseSpec {
        val cookiesList = extractCookiesFromHeaders(this.headers)
        val resolvedBody = bodyString ?: this.body?.decodeToString() ?: ""

        return NetworkResponseSpec(
            statusCode = this.statusCode,
            statusText = this.statusText,
            durationMs = durationMs,
            sizeBytes = sizeBytes,
            responseBody = resolvedBody,
            headers = this.headers,
            cookies = cookiesList
        )
    }

    /**
     * Converts a complete [HttpTransaction] feed entity into a unified [NetworkRequestSpec].
     *
     * @param bodyString Optional explicit string representation of request body payload.
     * @return Formatted [NetworkRequestSpec].
     */
    fun HttpTransaction.toNetworkRequestSpec(bodyString: String? = null): NetworkRequestSpec {
        return this.request.toNetworkRequestSpec(bodyString)
    }

    /**
     * Converts a complete [HttpTransaction] feed entity into a unified [NetworkResponseSpec].
     *
     * @param bodyString Optional explicit string representation of response body payload.
     * @return Formatted [NetworkResponseSpec], or null if response is unavailable.
     */
    fun HttpTransaction.toNetworkResponseSpec(bodyString: String? = null): NetworkResponseSpec? {
        return this.response?.toNetworkResponseSpec(
            durationMs = this.durationMs,
            sizeBytes = this.responseBodySize,
            bodyString = bodyString
        )
    }

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
            customMethod = this.customMethod,
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
            customMethod = this.customMethod,
            url = this.url,
            headers = requestHeaders,
            body = ApiRequestBody(content = this.bodyPayload, type = bodyTypeName),
            auth = this.auth
        )
    }

    private fun parseHttpMethod(method: String): Pair<HttpMethod, String?> {
        val upper = method.uppercase()
        return try {
            val enumVal = HttpMethod.valueOf(upper)
            enumVal to null
        } catch (_: Exception) {
            HttpMethod.CUSTOM to upper
        }
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

    private fun inferRequestBodyType(body: String, headers: List<Pair<String, String>>): RequestBodyType {
        if (body.isBlank()) return RequestBodyType.NONE
        val contentType = headers.firstOrNull { it.first.equals("Content-Type", ignoreCase = true) }?.second ?: ""
        val acceptType = headers.firstOrNull { it.first.equals("Accept", ignoreCase = true) }?.second ?: ""
        val trimmedBody = body.trimStart()

        return when {
            contentType.contains("graphql", ignoreCase = true) ||
            acceptType.contains("graphql", ignoreCase = true) ||
            trimmedBody.startsWith("query") ||
            trimmedBody.startsWith("mutation") ||
            (trimmedBody.startsWith("{") && (trimmedBody.contains("\"query\"") || trimmedBody.contains("\"mutation\""))) -> RequestBodyType.GRAPHQL

            contentType.contains("json", ignoreCase = true) ||
            trimmedBody.startsWith("{") ||
            trimmedBody.startsWith("[") -> RequestBodyType.JSON

            contentType.contains("xml", ignoreCase = true) ||
            trimmedBody.startsWith("<") -> RequestBodyType.XML

            contentType.contains("form-urlencoded", ignoreCase = true) ||
            contentType.contains("form-data", ignoreCase = true) -> RequestBodyType.FORM_DATA

            else -> RequestBodyType.RAW_TEXT
        }
    }
}
