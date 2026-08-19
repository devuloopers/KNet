package com.devuloopers.knet.connectivity.desktop.wifi

import kotlin.io.encoding.Base64

/** Typed values rendered into the packaged mobile Wi-Fi setup page. */
internal data class WifiSetupPageModel(
    val proxyHost: String,
    val proxyPort: Int,
    val certificateSha256: String,
)

/** Loads and safely renders the responsive Wi-Fi setup page resource. */
internal object WifiSetupPageRenderer {
    private const val RESOURCE_PATH = "/templates/wifi_setup_portal.html"
    private val unresolvedPlaceholder = Regex("\\{\\{[A-Za-z][A-Za-z0-9]*}}")
    private val template: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        checkNotNull(WifiSetupPageRenderer::class.java.getResourceAsStream(RESOURCE_PATH)) {
            "Wi-Fi setup page template is missing: $RESOURCE_PATH"
        }.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
    }

    fun render(model: WifiSetupPageModel): String {
        require(model.proxyPort in 1..65_535)
        require(model.certificateSha256.matches(Regex("[0-9a-f]{64}")))
        return template
            .replaceRequired("proxyHost", model.proxyHost.htmlEscape())
            .replaceRequired("proxyPort", model.proxyPort.toString())
            .replaceRequired("certificateSha256", model.certificateSha256.chunked(2).joinToString(":").htmlEscape())
            .also { rendered ->
                require(!unresolvedPlaceholder.containsMatchIn(rendered)) {
                    "Wi-Fi setup page contains an unresolved placeholder."
                }
            }
    }
}

/** Renders KNet's root CA as a valid Apple configuration profile payload. */
internal object AppleRootCertificateProfileRenderer {
    private const val RESOURCE_PATH = "/templates/apple_root_ca.mobileconfig.xml"
    private const val CERTIFICATE_PLACEHOLDER = "{{certificateBase64}}"
    private val template: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        checkNotNull(AppleRootCertificateProfileRenderer::class.java.getResourceAsStream(RESOURCE_PATH)) {
            "Apple root certificate profile template is missing: $RESOURCE_PATH"
        }.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
    }

    fun render(certificate: ByteArray): String {
        require(certificate.isNotEmpty()) { "Apple root certificate profile requires certificate bytes." }
        require(template.indexOf(CERTIFICATE_PLACEHOLDER) == template.lastIndexOf(CERTIFICATE_PLACEHOLDER)) {
            "Apple root certificate profile must contain one certificate placeholder."
        }
        require(CERTIFICATE_PLACEHOLDER in template) {
            "Apple root certificate profile certificate placeholder is missing."
        }
        return template.replace(CERTIFICATE_PLACEHOLDER, Base64.encode(certificate))
    }
}

private fun String.replaceRequired(name: String, value: String): String {
    val placeholder = "{{$name}}"
    require(indexOf(placeholder) >= 0) { "Wi-Fi setup page does not contain $placeholder." }
    return replace(placeholder, value)
}

private fun String.htmlEscape(): String = replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")
