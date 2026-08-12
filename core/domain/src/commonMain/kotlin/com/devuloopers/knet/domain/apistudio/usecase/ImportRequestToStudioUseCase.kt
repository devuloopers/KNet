package com.devuloopers.knet.domain.apistudio.usecase

import com.devuloopers.knet.domain.network.model.NetworkRequestSpec

/**
 * Result data holder emitted when a [NetworkRequestSpec] is normalized for API Studio import.
 *
 * @property spec Normalized strongly-typed network request specification.
 * @property displayTitle Suggested display title string for the imported request tab.
 */
public data class ImportedStudioRequest(
    val spec: NetworkRequestSpec,
    val displayTitle: String
)

/**
 * Domain UseCase validating and normalizing captured or authored [NetworkRequestSpec] instances
 * for import into API Studio draft sessions.
 *
 * SRP: Centralizes URL scheme normalization, header/cookie pair cleaning, and display title derivation.
 */
public class ImportRequestToStudioUseCase {

    /**
     * Normalizes the given [spec] and derives an appropriate tab display title.
     *
     * @param spec Input network request specification.
     * @param title Optional custom display title override.
     * @return [ImportedStudioRequest] containing normalized spec and computed display title.
     */
    public fun execute(
        spec: NetworkRequestSpec,
        title: String? = null
    ): ImportedStudioRequest {
        val rawUrl = spec.url.trim()
        val normalizedUrl = when {
            rawUrl.isBlank() -> "https://api.example.com"
            !rawUrl.contains("://") -> "https://$rawUrl"
            else -> rawUrl
        }

        val cleanedHeaders = spec.headers
            .filter { it.first.isNotBlank() }
            .map { it.first.trim() to it.second.trim() }

        val cleanedQueryParams = spec.queryParams
            .filter { it.first.isNotBlank() }
            .map { it.first.trim() to it.second.trim() }

        val cleanedCookies = spec.cookies
            .filter { it.first.isNotBlank() }
            .map { it.first.trim() to it.second.trim() }

        val normalizedSpec = spec.copy(
            url = normalizedUrl,
            headers = cleanedHeaders,
            queryParams = cleanedQueryParams,
            cookies = cleanedCookies
        )

        val computedTitle = title?.trim()?.takeIf { it.isNotBlank() }
            ?: deriveTitleFromUrl(normalizedUrl)

        return ImportedStudioRequest(
            spec = normalizedSpec,
            displayTitle = computedTitle
        )
    }

    private fun deriveTitleFromUrl(url: String): String {
        return try {
            val pathOrHost = url.substringAfter("://")
            val pathWithQuery = pathOrHost.substringAfter("/", "")
            val rawPath = pathWithQuery.substringBefore("?").trim('/')
            if (rawPath.isNotBlank()) {
                "/$rawPath"
            } else {
                val host = pathOrHost.substringBefore("/").substringBefore("?").trim()
                if (host.isNotBlank()) host else "Untitled Request"
            }
        } catch (_: Exception) {
            "Untitled Request"
        }
    }
}
