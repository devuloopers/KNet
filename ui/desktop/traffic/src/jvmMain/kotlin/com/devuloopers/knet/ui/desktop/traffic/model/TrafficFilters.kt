package com.devuloopers.knet.ui.desktop.traffic.model

/** Request-scheme filter kept separate from the negotiated HTTP version. */
enum class SchemeFilter(val label: String) {
    ALL("All"),
    HTTP("HTTP"),
    HTTPS("HTTPS"),
}

/** HTTP application-version filter matching either observed proxy connection leg. */
enum class HttpVersionFilter(val label: String) {
    ALL("Any version"),
    HTTP_1_0("HTTP/1.0"),
    HTTP_1_1("HTTP/1.1"),
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
