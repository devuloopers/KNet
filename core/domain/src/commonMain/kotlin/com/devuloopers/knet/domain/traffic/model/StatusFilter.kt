package com.devuloopers.knet.domain.traffic.model

/**
 * Strongly-typed enum representing HTTP status code range filter options for traffic inspection.
 *
 * @property label User-facing display label for UI rendering.
 * @property range Status code range matched by this filter, or null if matching all.
 */
enum class StatusFilter(val label: String, val range: IntRange? = null) {
    ALL("All"),
    STATUS_2XX("2xx", 200..299),
    STATUS_3XX("3xx", 300..399),
    STATUS_4XX("4xx", 400..499),
    STATUS_5XX("5xx", 500..599)
}
