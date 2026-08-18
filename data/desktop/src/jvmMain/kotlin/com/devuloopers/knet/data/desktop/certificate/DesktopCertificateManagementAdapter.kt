package com.devuloopers.knet.data.desktop.certificate

import com.devuloopers.knet.application.port.certificate.CertificateAuthoritySummary
import com.devuloopers.knet.application.port.certificate.CertificateManagementPort
import com.devuloopers.knet.application.port.certificate.ClientCertificateSummary
import com.devuloopers.knet.application.port.certificate.ClientCertificateFormat
import com.devuloopers.knet.application.port.certificate.MtlsRuleSpec
import com.devuloopers.knet.engine.certificate.CertificateManager
import com.devuloopers.knet.engine.certificate.EngineMtlsRule

/** Desktop/JCA certificate engine adapter kept below the application boundary. */
class DesktopCertificateManagementAdapter(
    private val manager: CertificateManager,
) : CertificateManagementPort {
    override suspend fun authoritySummary(): CertificateAuthoritySummary = CertificateAuthoritySummary(
        status = manager.getCaStatus(),
        subject = manager.getCaSubject(),
        issuer = manager.getCaIssuer(),
        serialNumber = manager.getCaSerialNumber(),
        signatureAlgorithm = manager.getCaSignatureAlgorithm(),
        validFrom = manager.getCaValidFrom(),
        validUntil = manager.getCaValidUntil(),
        sha1Fingerprint = manager.getCaSha1Fingerprint(),
        sha256Fingerprint = manager.getCaSha256Fingerprint(),
        trustedByOperatingSystem = manager.isCaTrustedByOs(),
    )

    override suspend fun installRootCertificate(): Boolean = manager.installRootCertificate()
    override suspend fun isRootCertificateTrusted(): Boolean = manager.isCaTrustedByOs()
    override suspend fun clientCertificates(): List<ClientCertificateSummary> = manager.getClientCertificates().map { value ->
        ClientCertificateSummary(
            value.alias, value.subject, value.host, value.expiration, value.enabled,
            ClientCertificateFormat.fromToken(value.format),
            value.daysUntilExpiration, value.subjectDn, value.issuerDn, value.serialNumber,
            value.sanList, value.publicKeyAlgorithm, value.sha256Fingerprint,
        )
    }
    override suspend fun importClientCertificate(path: String, alias: String, passphrase: String) =
        manager.importClientCertificate(path, alias, passphrase)
    override suspend fun exportClientCertificate(alias: String, destinationPath: String) =
        manager.exportClientCertificate(alias, destinationPath)
    override suspend fun deleteClientCertificate(alias: String) = manager.deleteClientCertificate(alias)
    override suspend fun setClientCertificateEnabled(alias: String, enabled: Boolean) =
        manager.toggleCertificateEnabled(alias, enabled)
    override suspend fun mtlsRules(): List<MtlsRuleSpec> = manager.getMtlsRules().map { value ->
        MtlsRuleSpec(value.ruleName, value.hostPattern, value.certificateAlias, value.enabled)
    }
    override suspend fun addMtlsRule(rule: MtlsRuleSpec) = manager.addMtlsRule(rule.toEngine())
    override suspend fun editMtlsRule(rule: MtlsRuleSpec) = manager.editMtlsRule(rule.toEngine())
    override suspend fun deleteMtlsRule(ruleName: String) = manager.deleteMtlsRule(ruleName)

    private fun MtlsRuleSpec.toEngine(): EngineMtlsRule =
        EngineMtlsRule(ruleName, hostPattern, certificateAlias, enabled)
}
