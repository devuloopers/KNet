package com.devuloopers.knet.ui.desktop.httppanel.model

/**
 * Closed set of sub-tabs supported by KNet Request and Response inspectors.
 *
 * @param label Human-readable sub-tab label text.
 */
public enum class InspectorSubTab(public val label: String) {
    PARAMS("Params"),
    AUTH("Auth"),
    HEADERS("Headers"),
    BODY("Body"),
    COOKIES("Cookies"),
    SCRIPTS("Scripts");

    public companion object {
        /** Default sub-tabs supported by Request Inspector/Editor. */
        public val RequestTabs: List<InspectorSubTab> = listOf(PARAMS, AUTH, HEADERS, BODY, COOKIES, SCRIPTS)

        /** Default sub-tabs supported by Response Inspector. */
        public val ResponseTabs: List<InspectorSubTab> = listOf(BODY, HEADERS, COOKIES)
    }
}
