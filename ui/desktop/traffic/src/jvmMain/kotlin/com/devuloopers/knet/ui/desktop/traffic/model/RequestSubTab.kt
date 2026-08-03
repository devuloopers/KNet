package com.devuloopers.knet.ui.desktop.traffic.model

/**
 * Enum representing secondary sub-tabs within the Request Inspection tab.
 *
 * @property displayName Human-readable label for the sub-tab button.
 */
public enum class RequestSubTab(val displayName: String) {
    HEADERS("Headers"),
    QUERY("Query"),
    BODY("Body")
}
