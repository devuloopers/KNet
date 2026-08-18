package com.devuloopers.knet.domain.traffic.model

/**
 * Strongly-typed enum representing HTTP method filter options for traffic inspection.
 *
 * @property label User-facing display label for UI rendering.
 */
enum class MethodFilter(val label: String) {
    ALL("All"),
    GET("GET"),
    POST("POST"),
    PUT("PUT"),
    PATCH("PATCH"),
    DELETE("DELETE"),
    HEAD("HEAD"),
    OPTIONS("OPTIONS")
}
