package com.devuloopers.knet.application.port.certificate

/** Application-facing CA metadata with no X.509/JCA implementation types. */
public data class CertificateAuthoritySummary(
    public val status: String = "MISSING",
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

public enum class ClientCertificateFormat {
    PKCS12,
    PEM,
    JKS;

    public companion object {
        public fun fromToken(value: String): ClientCertificateFormat = when (value.uppercase().trim()) {
            "PEM", "CRT", "CER" -> PEM
            "JKS", "KEYSTORE" -> JKS
            else -> PKCS12
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
    public suspend fun installRootCertificate(): Boolean
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
