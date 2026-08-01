package com.devuloopers.knet.ui.desktop.inspector.model

/**
 * Enum representing sub-tabs in [ResponseInspector].
 */
public enum class ResponseSubTab(public val label: String) {
    BODY("Body"),
    HEADERS("Headers"),
    COOKIES("Cookies"),
    TRAILERS("Trailers")
}
