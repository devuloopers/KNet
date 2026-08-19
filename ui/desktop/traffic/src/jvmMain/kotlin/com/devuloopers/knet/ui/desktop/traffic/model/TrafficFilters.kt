package com.devuloopers.knet.ui.desktop.traffic.model

/** Scheme/application-protocol filters that the current HTTP capture engine can identify exactly. */
enum class ProtocolFilter(val label: String) {
    ALL("All"),
    HTTP("HTTP"),
    HTTPS("HTTPS"),
    HTTP_2("HTTP/2"),
    HTTP_3("HTTP/3"),
}

/** HTTP method choices owned by the Traffic presentation. */
enum class MethodFilter(val label: String) {
    ALL("All"),
    GET("GET"),
    POST("POST"),
    PUT("PUT"),
    PATCH("PATCH"),
    DELETE("DELETE"),
    HEAD("HEAD"),
    OPTIONS("OPTIONS"),
}

/** HTTP response-status ranges owned by the Traffic presentation. */
enum class StatusFilter(val label: String, val range: IntRange? = null) {
    ALL("All"),
    STATUS_2XX("2xx", 200..299),
    STATUS_3XX("3xx", 300..399),
    STATUS_4XX("4xx", 400..499),
    STATUS_5XX("5xx", 500..599),
}
