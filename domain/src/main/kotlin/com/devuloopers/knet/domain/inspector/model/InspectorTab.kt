package com.devuloopers.knet.domain.inspector.model

/**
 * Enumeration of available tab segments inside the Transaction Inspector panel.
 */
enum class InspectorTab(val displayName: String) {
    OVERVIEW("Overview"),
    HEADERS("Headers"),
    REQUEST_BODY("Request Body"),
    RESPONSE_BODY("Response Body"),
    TIMING("Timing")
}
