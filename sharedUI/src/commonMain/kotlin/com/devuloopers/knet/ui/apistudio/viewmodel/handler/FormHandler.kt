package com.devuloopers.knet.ui.apistudio.viewmodel.handler

import com.devuloopers.knet.domain.apistudio.detector.UrlParameterExtractor
import com.devuloopers.knet.domain.apistudio.model.ApiRequestAuth
import com.devuloopers.knet.domain.apistudio.model.ApiRequestBody
import com.devuloopers.knet.domain.apistudio.model.HttpMethod
import com.devuloopers.knet.domain.apistudio.model.RequestHeader
import com.devuloopers.knet.domain.apistudio.model.SavedApiRequest
import com.devuloopers.knet.domain.apistudio.model.defaultHeaders

/**
 * Pure handler managing request form state updates (URL, HTTP method, headers, query params, auth, and request body).
 */
class FormHandler {
    private val urlParameterExtractor = UrlParameterExtractor()

    /**
     * Updates URL and extracts path parameters.
     */
    fun updateUrl(
        targetRequest: SavedApiRequest,
        newUrl: String
    ): Pair<SavedApiRequest, Map<String, String>> {
        val updated = targetRequest.copy(url = newUrl)
        val extractedPathParams = urlParameterExtractor.extract(newUrl).pathVariables
        return updated to extractedPathParams
    }

    /**
     * Updates HTTP Method verb.
     */
    fun updateMethod(
        targetRequest: SavedApiRequest,
        newMethod: HttpMethod
    ): SavedApiRequest {
        return targetRequest.copy(method = newMethod)
    }

    /**
     * Toggles header enabled state.
     */
    fun toggleHeader(
        targetRequest: SavedApiRequest,
        headerKey: String
    ): SavedApiRequest {
        val updatedHeaders = targetRequest.headers.map { h ->
            if (h.key.equals(headerKey, ignoreCase = true)) h.copy(isEnabled = !h.isEnabled) else h
        }
        return targetRequest.copy(headers = updatedHeaders)
    }

    /**
     * Updates a header key.
     */
    fun updateHeaderKey(
        targetRequest: SavedApiRequest,
        oldKey: String,
        newKey: String
    ): SavedApiRequest {
        val updatedHeaders = targetRequest.headers.map { h ->
            if (h.key.equals(oldKey, ignoreCase = true)) h.copy(key = newKey) else h
        }
        return targetRequest.copy(headers = updatedHeaders)
    }

    /**
     * Updates a header value.
     */
    fun updateHeaderValue(
        targetRequest: SavedApiRequest,
        key: String,
        value: String
    ): SavedApiRequest {
        val updatedHeaders = targetRequest.headers.map { h ->
            if (h.key.equals(key, ignoreCase = true)) h.copy(value = value) else h
        }
        return targetRequest.copy(headers = updatedHeaders)
    }

    /**
     * Appends an empty header row.
     */
    fun addHeader(targetRequest: SavedApiRequest): SavedApiRequest {
        val newHeader = RequestHeader(key = "", value = "", isEnabled = true)
        return targetRequest.copy(headers = targetRequest.headers + newHeader)
    }

    /**
     * Removes a header row by key.
     */
    fun removeHeader(
        targetRequest: SavedApiRequest,
        headerKey: String
    ): SavedApiRequest {
        val updatedHeaders = targetRequest.headers.filterNot { it.key.equals(headerKey, ignoreCase = true) }
        return targetRequest.copy(headers = updatedHeaders)
    }

    /**
     * Restores default recommended headers.
     */
    fun restoreDefaultHeaders(targetRequest: SavedApiRequest): SavedApiRequest {
        val existingKeys = targetRequest.headers.map { it.key.lowercase() }.toSet()
        val missingDefaults = defaultHeaders().filter { it.key.lowercase() !in existingKeys }
        return targetRequest.copy(headers = missingDefaults + targetRequest.headers)
    }

    /**
     * Updates authentication model type.
     */
    fun updateAuthType(
        targetRequest: SavedApiRequest,
        authTypeLabel: String
    ): SavedApiRequest {
        val currentAuth = targetRequest.auth
        val newAuth = when (authTypeLabel) {
            "Bearer Token" -> ApiRequestAuth.Bearer(if (currentAuth is ApiRequestAuth.Bearer) currentAuth.token else "")
            "Basic Auth" -> ApiRequestAuth.Basic(
                username = if (currentAuth is ApiRequestAuth.Basic) currentAuth.username else "",
                password = if (currentAuth is ApiRequestAuth.Basic) currentAuth.password else ""
            )
            "API Key" -> ApiRequestAuth.ApiKey(
                name = if (currentAuth is ApiRequestAuth.ApiKey) currentAuth.name else "X-API-Key",
                value = if (currentAuth is ApiRequestAuth.ApiKey) currentAuth.value else "",
                location = if (currentAuth is ApiRequestAuth.ApiKey) currentAuth.location else "Header"
            )
            "OAuth 2.0" -> ApiRequestAuth.OAuth2(
                token = if (currentAuth is ApiRequestAuth.OAuth2) currentAuth.token else "",
                headerPrefix = if (currentAuth is ApiRequestAuth.OAuth2) currentAuth.headerPrefix else "Bearer"
            )
            "AWS Signature" -> ApiRequestAuth.AwsSignature(
                accessKey = if (currentAuth is ApiRequestAuth.AwsSignature) currentAuth.accessKey else "",
                secretKey = if (currentAuth is ApiRequestAuth.AwsSignature) currentAuth.secretKey else "",
                region = if (currentAuth is ApiRequestAuth.AwsSignature) currentAuth.region else "us-east-1",
                service = if (currentAuth is ApiRequestAuth.AwsSignature) currentAuth.service else "s3"
            )
            "Inherit Auth" -> ApiRequestAuth.Inherit
            else -> ApiRequestAuth.None
        }
        return targetRequest.copy(auth = newAuth)
    }

    /**
     * Updates request body content.
     */
    fun updateRequestBody(
        targetRequest: SavedApiRequest,
        newContent: String
    ): SavedApiRequest {
        return targetRequest.copy(body = targetRequest.body.copy(content = newContent))
    }
}
