package com.devuloopers.knet.ui.desktop.inspector.model

/**
 * Enum representing sub-tabs in [RequestInspector].
 */
public enum class RequestSubTab(public val label: String) {
    HEADERS("Headers"),
    PARAMS("Params"),
    COOKIES("Cookies"),
    BODY("Body")
}
