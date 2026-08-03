package com.devuloopers.knet.ui.desktop.traffic.model

/**
 * Enum representing secondary sub-tabs within the Response Inspection tab.
 *
 * @property displayName Human-readable label for the sub-tab button.
 */
public enum class ResponseSubTab(val displayName: String) {
    HEADERS("Headers"),
    COOKIES("Cookies"),
    BODY("Body")
}
