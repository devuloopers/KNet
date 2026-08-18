package com.devuloopers.knet.ui.desktop.httppanel.model

import com.devuloopers.knet.domain.collection.model.ApiRequestAuth

/**
 * Standard authentication types supported in KNet API Studio.
 */
enum class AuthType(val label: String) {
    NO_AUTH("No Auth"),
    BEARER_TOKEN("Bearer Token"),
    BASIC_AUTH("Basic Auth"),
    API_KEY("API Key"),
    INHERIT("Inherit auth from parent")
}

/**
 * Target location for API Key authorization credentials.
 */
enum class ApiKeyLocation(val label: String) {
    HEADER("Header"),
    QUERY_PARAMS("Query Params")
}

/**
 * State DTO holding authorization configuration for API Studio requests.
 */
data class AuthState(
    val authType: AuthType = AuthType.NO_AUTH,
    val bearerToken: String = "",
    val basicUsername: String = "",
    val basicPassword: String = "",
    val apiKeyName: String = "",
    val apiKeyValue: String = "",
    val apiKeyLocation: ApiKeyLocation = ApiKeyLocation.HEADER
)

/**
 * Extension mapper converting domain [com.devuloopers.knet.domain.collection.model.ApiRequestAuth] model into UI presentation [AuthState].
 *
 * @return Mapped UI presentation [AuthState].
 */
fun ApiRequestAuth.toAuthState(): AuthState = when (this) {
    ApiRequestAuth.None -> AuthState(authType = AuthType.NO_AUTH)
    ApiRequestAuth.Inherit -> AuthState(authType = AuthType.INHERIT)

    is ApiRequestAuth.Bearer -> AuthState(
        authType = AuthType.BEARER_TOKEN,
        bearerToken = token
    )

    is ApiRequestAuth.Basic -> AuthState(
        authType = AuthType.BASIC_AUTH,
        basicUsername = username,
        basicPassword = password
    )

    is ApiRequestAuth.ApiKey -> AuthState(
        authType = AuthType.API_KEY,
        apiKeyName = name,
        apiKeyValue = value,
        apiKeyLocation = if (location.contains(
                "Query",
                ignoreCase = true
            )
        ) ApiKeyLocation.QUERY_PARAMS else ApiKeyLocation.HEADER
    )

    is ApiRequestAuth.OAuth2 -> AuthState(
        authType = AuthType.BEARER_TOKEN,
        bearerToken = token,
    )

    is ApiRequestAuth.AwsSignature -> AuthState(authType = AuthType.NO_AUTH)
}

/** Converts the shared HTTP authentication editor state to its canonical authored value. */
fun AuthState.toApiRequestAuth(): ApiRequestAuth = when (authType) {
    AuthType.NO_AUTH -> ApiRequestAuth.None
    AuthType.BEARER_TOKEN -> ApiRequestAuth.Bearer(bearerToken)
    AuthType.BASIC_AUTH -> ApiRequestAuth.Basic(username = basicUsername, password = basicPassword)
    AuthType.API_KEY -> ApiRequestAuth.ApiKey(
        name = apiKeyName.ifBlank { "X-API-Key" },
        value = apiKeyValue,
        location = apiKeyLocation.label,
    )
    AuthType.INHERIT -> ApiRequestAuth.Inherit
}
