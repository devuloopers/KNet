package com.devuloopers.knet.domain.clientNetwork.model

/**
 * Strongly-typed domain representation of supported request body payload formats.
 */
public enum class RequestBodyType {
    NONE,
    JSON,
    XML,
    FORM_DATA,
    X_WWW_FORM_URLENCODED,
    MULTIPART,
    GRAPHQL,
    RAW_TEXT
}
