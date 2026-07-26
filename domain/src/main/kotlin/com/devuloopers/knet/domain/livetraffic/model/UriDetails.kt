package com.devuloopers.knet.domain.livetraffic.model

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
            var host = ""
            var path = "/"
            val params = mutableMapOf<String, String>()

            try {
                val uri = URI(url)
                host = uri.host ?: ""
                path = (uri.path ?: "/").ifEmpty { "/" }
                val query = uri.query
                if (!query.isNullOrEmpty()) {
                    path = "$path?$query"
                    query.split("&").forEach { pair ->
                        val parts = pair.split("=", limit = 2)
                        if (parts.size == 2) {
                            params[parts[0]] = parts[1]
                        } else if (parts.size == 1) {
                            params[parts[0]] = ""
                        }
                    }
                }
            } catch (_: Exception) {
                host = url
            }

            return UriDetails(host, path, params)
        }
    }
}
