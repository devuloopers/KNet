package com.devuloopers.knet.engine.client.model

/**
 * Supported request body payload types (matching Postman body modes).
 */
enum class RequestBodyType {
    NONE,
    JSON,
    XML,
    FORM_URLENCODED,
    MULTIPART,
    GRAPHQL,
    RAW_TEXT
}

/**
 * Supported authorization types for API calls.
 */
enum class AuthType {
    NONE,
    BEARER_TOKEN,
    BASIC_AUTH,
    API_KEY
}
