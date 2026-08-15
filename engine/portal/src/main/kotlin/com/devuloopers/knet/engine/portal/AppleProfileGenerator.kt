package com.devuloopers.knet.engine.portal

import java.security.cert.X509Certificate
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Utility for generating Apple `.mobileconfig` Configuration Profiles.
 *
 * Configuration Profiles allow iOS devices (iPhones, iPads) to download and install Root CA
 * certificates into the iOS Trust Store with proper payload metadata (`com.apple.security.root`).
 */
object AppleProfileGenerator {

    private const val TEMPLATE_PATH = "templates/knet-ca.mobileconfig.template"

    /**
     * Generates a fully formatted Apple `.mobileconfig` XML Property List (plist) string.
     *
     * @param caCertificate The [X509Certificate] instance of KNet's Root CA.
     * @param displayName The user-facing display name for the configuration profile (default: "KNet Root CA").
     * @param organization The organization name for the configuration profile (default: "Devuloopers").
     * @return Formatted XML string compliant with Apple Configuration Profile specifications.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun generateMobileConfig(
        caCertificate: X509Certificate,
        displayName: String = "KNet Root CA",
        organization: String = "Devuloopers"
    ): String {
        val base64Cert = Base64.Mime.encode(caCertificate.encoded)
        val template = TemplateLoader.load(TEMPLATE_PATH)

        return template
            .replace("{{BASE64_CERT}}", base64Cert)
            .replace("{{DISPLAY_NAME}}", displayName)
            .replace("{{ORGANIZATION}}", organization)
    }
}

