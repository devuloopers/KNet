package com.devuloopers.knet.connectivity.desktop.certificate

import kotlin.io.encoding.Base64

/** Renders KNet's public root certificate into the packaged Apple configuration-profile resource. */
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
