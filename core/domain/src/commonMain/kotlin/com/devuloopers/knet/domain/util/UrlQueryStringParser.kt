package com.devuloopers.knet.domain.util

/**
 * Pure domain utility providing bi-directional parsing and reconstruction of URL query strings.
 */
object UrlQueryStringParser {

    /**
     * Parses a raw URL string into key-value query parameter pairs.
     *
     * @param url Target URL string containing optional `?key=value` query parameters.
     * @return List of key-value pairs extracted from query string.
     */
    fun parseQueryParams(url: String): List<Pair<String, String>> {
        if (!url.contains("?")) return emptyList()
        val queryString = url.substringAfter("?").substringBefore("#")
        if (queryString.isBlank()) return emptyList()
        return queryString.split("&").mapNotNull { pair ->
            val parts = pair.split("=", limit = 2)
            val key = parts.getOrNull(0)?.trim() ?: ""
            if (key.isNotBlank()) {
                val value = parts.getOrNull(1)?.trim() ?: ""
                key to value
            } else null
        }
    }

    /**
     * Reconstructs a target URL string by combining a base URL with updated query parameter key-value pairs.
     *
     * @param baseUrl The base URL string (with or without existing query string).
     * @param queryParams Key-value pairs representing request query parameters.
     * @return Updated URL string with formatted query string appended.
     */
    fun rebuildUrlWithQueryParams(baseUrl: String, queryParams: List<Pair<String, String>>): String {
        val cleanBaseUrl = if (baseUrl.contains("?")) baseUrl.substringBefore("?") else baseUrl
        val activeParams = queryParams.filter { it.first.isNotBlank() }
        return if (activeParams.isNotEmpty()) {
            val queryString = activeParams.joinToString("&") { "${it.first}=${it.second}" }
            "$cleanBaseUrl?$queryString"
        } else {
            cleanBaseUrl
        }
    }
}
