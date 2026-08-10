package com.devuloopers.knet.ui.desktop.certificate.model

/**
 * Strongly-typed domain enum representing all supported client certificate file formats and their display descriptors.
 *
 * Adding a new enum variant here automatically updates file dialog filters, extension lists,
 * and UI text labels across the entire application without needing manual string updates elsewhere.
 *
 * @property extension The raw lowercase file extension without a leading dot (e.g. "p12").
 * @property displayName Human-readable format name (e.g. "PKCS#12 (.p12)").
 * @property isKeyContainer True if format typically contains both private key and certificate chain.
 */
public enum class CertificateFileFormat(
    public val extension: String,
    public val displayName: String,
    public val isKeyContainer: Boolean = false
) {
    PKCS12_P12("p12", "PKCS#12 (.p12)", isKeyContainer = true),
    PKCS12_PFX("pfx", "PKCS#12 (.pfx)", isKeyContainer = true),
    PEM("pem", "PEM Certificate"),
    CRT("crt", "CRT Certificate"),
    CER("cer", "CER Certificate");

    public val dotExtension: String get() = ".$extension"

    public companion object {
        /**
         * Raw extension whitelist derived dynamically from [entries].
         */
        public val allExtensions: List<String> get() = entries.map { it.extension }

        /**
         * Dynamic slash-separated extension descriptor string (e.g. ".p12 / .pfx / .pem / .crt / .cer").
         */
        public val formattedExtensionsLabel: String get() = entries.joinToString(" / ") { it.dotExtension }

        /**
         * Dynamic description text summarizing supported formats across cards and empty states.
         */
        public val defaultDescription: String get() =
            "Import a PKCS#12 (.p12/.pfx), PEM, or CER certificate to authenticate mTLS connections for specific domains."

        /**
         * Dynamic footer summary label string.
         */
        public val supportsFooterLabel: String get() = "Supports: PKCS#12, PEM (CRT / CER + Key)"
    }
}
