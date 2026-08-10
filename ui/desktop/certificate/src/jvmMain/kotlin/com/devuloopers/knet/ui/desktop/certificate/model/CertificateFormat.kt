package com.devuloopers.knet.ui.desktop.certificate.model

/**
 * Represents the format of an imported client certificate.
 */
public enum class CertificateFormat {
    PKCS12,
    PEM,
    JKS;

    public companion object {
        /**
         * Safely maps a raw string format identifier from engine layer into [CertificateFormat].
         */
        public fun fromString(value: String): CertificateFormat {
            return when (value.uppercase().trim()) {
                "PEM", "CRT", "CER" -> PEM
                "JKS", "KEYSTORE" -> JKS
                else -> PKCS12
            }
        }
    }
}

/**
 * Converts a UI [CertificateFileFormat] to its corresponding engine format token.
 */
public fun CertificateFileFormat.toEngineFormat(): CertificateFormat {
    return when (this) {
        CertificateFileFormat.PEM, CertificateFileFormat.CRT, CertificateFileFormat.CER -> CertificateFormat.PEM
        CertificateFileFormat.PKCS12_P12, CertificateFileFormat.PKCS12_PFX -> CertificateFormat.PKCS12
    }
}
