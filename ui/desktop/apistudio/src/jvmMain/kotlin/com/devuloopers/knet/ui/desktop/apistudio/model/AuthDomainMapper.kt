package com.devuloopers.knet.ui.desktop.apistudio.model

import com.devuloopers.knet.domain.collection.model.ApiRequestAuth

/**
 * Bi-directional converter mapping presentation layer [AuthState] to/from domain entity [ApiRequestAuth].
 */
public object AuthDomainMapper {

    /**
     * Converts a domain [ApiRequestAuth] model into a presentation [AuthState].
     *
     * @param domainAuth Domain authentication entity.
     * @return Transformed [AuthState] presentation model.
     */
    public fun mapDomainAuthToAuthState(domainAuth: ApiRequestAuth): AuthState {
        return when (domainAuth) {
            is ApiRequestAuth.None -> AuthState(authType = AuthType.NO_AUTH)
            is ApiRequestAuth.Bearer -> AuthState(authType = AuthType.BEARER_TOKEN, bearerToken = domainAuth.token)
            is ApiRequestAuth.Basic -> AuthState(authType = AuthType.BASIC_AUTH, basicUsername = domainAuth.username, basicPassword = domainAuth.password)
            is ApiRequestAuth.ApiKey -> {
                val loc = ApiKeyLocation.entries.find { it.label.equals(domainAuth.location, ignoreCase = true) } ?: ApiKeyLocation.HEADER
                AuthState(authType = AuthType.API_KEY, apiKeyName = domainAuth.name, apiKeyValue = domainAuth.value, apiKeyLocation = loc)
            }
            is ApiRequestAuth.Inherit -> AuthState(authType = AuthType.INHERIT)
            is ApiRequestAuth.OAuth2 -> AuthState(authType = AuthType.BEARER_TOKEN, bearerToken = domainAuth.token)
            else -> AuthState(authType = AuthType.NO_AUTH)
        }
    }

    /**
     * Converts a presentation [AuthState] into a domain [ApiRequestAuth] entity.
     *
     * @param authState Active UI authentication state DTO.
     * @return Transformed [ApiRequestAuth] domain entity.
     */
    public fun mapAuthStateToDomainAuth(authState: AuthState): ApiRequestAuth {
        return when (authState.authType) {
            AuthType.NO_AUTH -> ApiRequestAuth.None
            AuthType.BEARER_TOKEN -> ApiRequestAuth.Bearer(authState.bearerToken)
            AuthType.BASIC_AUTH -> ApiRequestAuth.Basic(username = authState.basicUsername, password = authState.basicPassword)
            AuthType.API_KEY -> ApiRequestAuth.ApiKey(
                name = authState.apiKeyName.ifBlank { "X-API-Key" },
                value = authState.apiKeyValue,
                location = authState.apiKeyLocation.label
            )
            AuthType.INHERIT -> ApiRequestAuth.Inherit
        }
    }
}
