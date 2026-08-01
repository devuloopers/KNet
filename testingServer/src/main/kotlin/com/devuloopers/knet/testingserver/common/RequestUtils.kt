package com.devuloopers.knet.testingserver.common

import org.springframework.web.reactive.function.server.ServerRequest

/**
 * Utility functions for extracting headers, query parameters, and cookies from [ServerRequest].
 */
object RequestUtils {

    fun extractHeaders(request: ServerRequest): Map<String, String> {
        val map = mutableMapOf<String, String>()
        request.headers().asHttpHeaders().forEach { key, values ->
            map[key] = values.joinToString(", ")
        }
        return map
    }

    fun extractQueryParams(request: ServerRequest): Map<String, String> {
        val map = mutableMapOf<String, String>()
        request.queryParams().forEach { key, values ->
            map[key] = values.firstOrNull() ?: ""
        }
        return map
    }

    fun extractCookies(request: ServerRequest): Map<String, String> {
        val map = mutableMapOf<String, String>()
        request.cookies().forEach { key, httpCookies ->
            map[key] = httpCookies.firstOrNull()?.value ?: ""
        }
        return map
    }
}
