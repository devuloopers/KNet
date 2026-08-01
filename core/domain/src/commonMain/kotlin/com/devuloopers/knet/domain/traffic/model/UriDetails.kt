package com.devuloopers.knet.domain.traffic.model

import java.net.URI

/**
 * Parsed URL components containing host, path with query string, and extracted query parameter map.
 */
data class UriDetails(
    val host: String,
    val path: String,
    val queryParams: Map<String, String>
) {
    companion object {
        /**
         * Parses a raw URL string into [UriDetails] cleanly in a single pass.
         */
        fun parse(url: String): UriDetails {
            var path = "/"
            val params = mutableMapOf<String, String>()

            val host = try {
                val uri = URI(url)
                val parsedPath = (uri.path ?: "/").ifEmpty { "/" }
                val query = uri.query
                path = if (!query.isNullOrEmpty()) "$parsedPath?$query" else parsedPath
                if (!query.isNullOrEmpty()) {
                    query.split("&").forEach { pair ->
                        val parts = pair.split("=", limit = 2)
                        if (parts.size == 2) {
                            params[parts[0]] = parts[1]
                        } else if (parts.size == 1) {
                            params[parts[0]] = ""
                        }
                    }
                }
                uri.host ?: ""
            } catch (_: Exception) {
                url
            }

            return UriDetails(host, path, params)
        }
    }
}
