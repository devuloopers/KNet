package com.devuloopers.knet.engine.portal

/**
 * Utility for rendering HTML templates for KNet's embedded mobile setup web portal (`/setup`).
 */
object PortalHtmlRenderer {

    private const val TEMPLATE_PATH = "templates/setup_portal.html.template"

    /**
     * Renders a responsive HTML5 setup portal web page from resource templates.
     *
     * @param desktopIp The LAN IP address of the KNet Desktop host machine.
     * @param proxyPort The active HTTP proxy port (default: 8080).
     * @return Clean HTML string for browser display.
     */
    fun renderSetupPage(desktopIp: String, proxyPort: Int): String {
        val template = TemplateLoader.load(TEMPLATE_PATH)

        return template
            .replace("{{DESKTOP_IP}}", desktopIp)
            .replace("{{PROXY_PORT}}", proxyPort.toString())
    }
}
