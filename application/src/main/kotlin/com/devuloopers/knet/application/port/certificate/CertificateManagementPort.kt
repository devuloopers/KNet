package com.devuloopers.knet.application.port.certificate

/** Application-facing CA metadata with no X.509/JCA implementation types. */
public data class CertificateAuthoritySummary(
    public val status: CertificateAuthorityStatus = CertificateAuthorityStatus.MISSING,
    public val subject: String = "",
    public val issuer: String = "",
    public val serialNumber: String = "",
    public val signatureAlgorithm: String = "",
    public val validFrom: String = "",
    public val validUntil: String = "",
    public val sha1Fingerprint: String = "",
    public val sha256Fingerprint: String = "",
    public val trustedByOperatingSystem: Boolean = false,
)

/** Lifecycle states the product can render without parsing engine-owned strings. */
public enum class CertificateAuthorityStatus {
    AVAILABLE,
    MISSING,
    EXPIRED,
    INVALID,
}

/** Typed outcome of an operating-system Root CA trust operation. */
public sealed interface TrustInstallationResult {
    public data object Installed : TrustInstallationResult

    public data class ManualActionRequired(
        public val message: String,
        public val instructions: String,
    ) : TrustInstallationResult

    public data class Failed(
        public val message: String,
    ) : TrustInstallationResult
}

public enum class ClientCertificateFormat {
    PKCS12,
    PEM,
    JKS;

    public companion object {
        public fun fromToken(value: String): ClientCertificateFormat = when (value.uppercase().trim()) {
            "PKCS12", "P12", "PFX" -> PKCS12
            "PEM", "CRT", "CER" -> PEM
            "JKS", "KEYSTORE" -> JKS
            else -> throw IllegalArgumentException("Unsupported client certificate format '$value'.")
        }
    }
}

public data class ClientCertificateSummary(
    public val alias: String,
    public val subject: String,
    public val host: String,
    public val expiration: String,
    public val enabled: Boolean = true,
    public val format: ClientCertificateFormat = ClientCertificateFormat.PKCS12,
    public val daysUntilExpiration: Int = 365,
    public val subjectDn: String = "",
    public val issuerDn: String = "",
    public val serialNumber: String = "",
    public val sanList: List<String> = emptyList(),
    public val publicKeyAlgorithm: String = "RSA 2048-bit",
    public val sha256Fingerprint: String = "",
)

public data class MtlsRuleSpec(
    public val ruleName: String,
    public val hostPattern: String,
    public val certificateAlias: String,
    public val enabled: Boolean = true,
)

/** Certificate/trust operations used by UI and application features, independent from the engine. */
public interface CertificateManagementPort {
    public suspend fun authoritySummary(): CertificateAuthoritySummary
    public suspend fun installRootCertificate(): TrustInstallationResult
    public suspend fun isRootCertificateTrusted(): Boolean
    public suspend fun clientCertificates(): List<ClientCertificateSummary>
    public suspend fun importClientCertificate(path: String, alias: String, passphrase: String = "")
    public suspend fun exportClientCertificate(alias: String, destinationPath: String)
    public suspend fun deleteClientCertificate(alias: String)
    public suspend fun setClientCertificateEnabled(alias: String, enabled: Boolean)
    public suspend fun mtlsRules(): List<MtlsRuleSpec>
    public suspend fun addMtlsRule(rule: MtlsRuleSpec)
    public suspend fun editMtlsRule(rule: MtlsRuleSpec)
    public suspend fun deleteMtlsRule(ruleName: String)
}
