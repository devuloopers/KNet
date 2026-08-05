package com.devuloopers.knet.ui.desktop.apistudio.model

/**
 * Standard authentication types supported in KNet API Studio.
 */
public enum class AuthType(val label: String) {
    NO_AUTH("No Auth"),
    BEARER_TOKEN("Bearer Token"),
    BASIC_AUTH("Basic Auth"),
    API_KEY("API Key"),
    INHERIT("Inherit auth from parent")
}

/**
 * Target location for API Key authorization credentials.
 */
public enum class ApiKeyLocation(val label: String) {
    HEADER("Header"),
    QUERY_PARAMS("Query Params")
}

/**
 * State DTO holding authorization configuration for API Studio requests.
 */
public data class AuthState(
    val authType: AuthType = AuthType.NO_AUTH,
    val bearerToken: String = "",
    val basicUsername: String = "",
    val basicPassword: String = "",
    val apiKeyName: String = "",
    val apiKeyValue: String = "",
    val apiKeyLocation: ApiKeyLocation = ApiKeyLocation.HEADER
)
