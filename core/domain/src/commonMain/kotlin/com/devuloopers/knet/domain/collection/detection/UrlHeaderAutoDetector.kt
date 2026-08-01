package com.devuloopers.knet.domain.collection.detection

/**
 * Result produced by [UrlParameterExtractor] when parsing a URL string.
 *
 * @property pathVariables Key-value map of path variable names extracted from `:variable` tokens.
 * @property queryParameters Key-value map of query string parameters extracted after `?`.
 */
data class UrlParseResult(
    val pathVariables: Map<String, String>,
    val queryParameters: Map<String, String>
)

/**
 * Lightweight URL parameter extractor that parses path variables (`:id`) and query parameters
 * (`?sort=desc&limit=20`) from a typed URL string.
 */
class UrlParameterExtractor {

    /**
     * Parses path variables and query parameters from the given URL string.
     *
     * @param url The URL string typed by the user (may be incomplete while typing).
     * @return [UrlParseResult] containing path variables and query parameters.
     */
    fun extract(url: String): UrlParseResult {
        if (url.isBlank()) return UrlParseResult(emptyMap(), emptyMap())

        val pathVars = mutableMapOf<String, String>()
        val queryParams = mutableMapOf<String, String>()

        // Extract path variables: e.g. /users/:id/posts/:postId
        val pathSegmentRegex = Regex(":([^/?&]+)")
        pathSegmentRegex.findAll(url).forEach { match ->
            val varName = match.groupValues[1]
            if (varName.isNotBlank()) {
                pathVars[varName] = ""
            }
        }

        // Extract query parameters: e.g. ?sort=desc&limit=20
        if (url.contains("?")) {
            val queryString = url.substringAfter("?").substringBefore("#")
            queryString.split("&").forEach { pair ->
                val parts = pair.split("=")
                if (parts.isNotEmpty() && parts[0].isNotBlank()) {
                    queryParams[parts[0]] = if (parts.size > 1) parts[1] else ""
                }
            }
        }

        return UrlParseResult(
            pathVariables = pathVars,
            queryParameters = queryParams
        )
    }
}
