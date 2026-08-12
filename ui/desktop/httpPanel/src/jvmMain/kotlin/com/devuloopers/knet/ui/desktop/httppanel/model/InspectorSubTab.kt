package com.devuloopers.knet.ui.desktop.httppanel.model

/**
 * Closed set of sub-tabs supported by KNet Request and Response inspectors.
 *
 * @param label Human-readable sub-tab label text.
 */
public enum class InspectorSubTab(public val label: String) {
    BODY("Body"),
    HEADERS("Headers"),
    PARAMS("Params"),
    COOKIES("Cookies");

    public companion object {
        /** Default sub-tabs supported by Request Inspector. */
        public val RequestTabs: List<InspectorSubTab> = listOf(BODY, HEADERS, PARAMS, COOKIES)

        /** Default sub-tabs supported by Response Inspector. */
        public val ResponseTabs: List<InspectorSubTab> = listOf(BODY, HEADERS, COOKIES)
    }
}
