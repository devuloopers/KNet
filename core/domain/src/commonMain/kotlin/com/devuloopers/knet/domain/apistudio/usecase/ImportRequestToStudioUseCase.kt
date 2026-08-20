package com.devuloopers.knet.domain.apistudio.usecase

import com.devuloopers.knet.domain.network.model.NetworkRequestSpec

/**
 * Result data holder emitted when a [NetworkRequestSpec] is normalized for API Studio import.
 *
 * @property spec Normalized strongly-typed network request specification.
 * @property requestedTitle Optional caller-supplied title. Generated naming is resolved from the canonical request
 * after the normalized specification has been hydrated into API Studio.
 */
data class ImportedStudioRequest(
    val spec: NetworkRequestSpec,
    val requestedTitle: String?
)

/**
 * Domain UseCase validating and normalizing captured or authored [NetworkRequestSpec] instances
 * for import into API Studio draft sessions.
 *
 * SRP: Centralizes URL scheme normalization and header/cookie pair cleaning. Request naming belongs to the
 * extensible canonical request-name pipeline.
 */
class ImportRequestToStudioUseCase {

    /**
     * Normalizes the given [spec] and retains an optional explicit title.
     *
     * @param spec Input network request specification.
     * @param title Optional custom display title override.
     * @return [ImportedStudioRequest] containing the normalized spec and trimmed explicit title.
     */
    fun execute(
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

        return ImportedStudioRequest(
            spec = normalizedSpec,
            requestedTitle = title?.trim()?.takeIf { it.isNotBlank() }
        )
    }
}
