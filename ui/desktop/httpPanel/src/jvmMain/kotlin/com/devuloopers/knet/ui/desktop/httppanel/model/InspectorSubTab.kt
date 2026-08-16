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
        /** Sub-tabs supported by Request Inspector for read-only captured traffic (Body | Headers | Params | Cookies). */
        public val RequestInspectorTabs: List<InspectorSubTab> = listOf(BODY, HEADERS, PARAMS, COOKIES)

        /** Sub-tabs supported by interactive Request Editors (API Studio, Composer). */
        public val RequestEditorTabs: List<InspectorSubTab> = listOf(PARAMS, AUTH, HEADERS, BODY, COOKIES, SCRIPTS)

        /** Sub-tabs supported by Response Inspector (Body | Headers | Cookies). */
        public val ResponseInspectorTabs: List<InspectorSubTab> = listOf(BODY, HEADERS, COOKIES)

        /** Sub-tabs supported by interactive Response Editors (Mocking / Interception Drawer). */
        public val ResponseEditorTabs: List<InspectorSubTab> = listOf(BODY, HEADERS, COOKIES)

        /** Backwards-compatible alias for Request Inspector. */
        public val RequestTabs: List<InspectorSubTab> get() = RequestInspectorTabs

        /** Backwards-compatible alias for Response Inspector. */
        public val ResponseTabs: List<InspectorSubTab> get() = ResponseInspectorTabs
    }
}
