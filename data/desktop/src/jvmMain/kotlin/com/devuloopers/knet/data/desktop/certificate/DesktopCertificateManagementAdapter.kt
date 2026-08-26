package com.devuloopers.knet.data.desktop.certificate

import com.devuloopers.knet.application.contract.certificate.CertificateAuthoritySummary
import com.devuloopers.knet.application.contract.certificate.CertificateAuthorityStatus
import com.devuloopers.knet.application.contract.certificate.CertificateManagement
import com.devuloopers.knet.application.contract.certificate.ClientCertificateFormat
import com.devuloopers.knet.application.contract.certificate.ClientCertificateSummary
import com.devuloopers.knet.application.contract.certificate.MtlsRuleSpec
import com.devuloopers.knet.application.contract.certificate.TrustInstallationResult
import com.devuloopers.knet.engine.certificate.CertificateManager
import com.devuloopers.knet.engine.certificate.CertificateAuthorityStatus as EngineCertificateAuthorityStatus
import com.devuloopers.knet.engine.certificate.EngineMtlsRule

/** Desktop/JCA certificate engine adapter kept below the application boundary. */
class DesktopCertificateManagementAdapter(
    private val manager: CertificateManager,
    private val rootTrustController: DesktopRootTrustController,
) : CertificateManagement {
    override suspend fun authoritySummary(): CertificateAuthoritySummary = manager.getAuthorityDetails().let { details ->
        CertificateAuthoritySummary(
            status = when (details.status) {
                EngineCertificateAuthorityStatus.AVAILABLE -> CertificateAuthorityStatus.AVAILABLE
                EngineCertificateAuthorityStatus.EXPIRED -> CertificateAuthorityStatus.EXPIRED
                EngineCertificateAuthorityStatus.INVALID -> CertificateAuthorityStatus.INVALID
            },
            subject = details.subject,
            issuer = details.issuer,
            serialNumber = details.serialNumber,
            signatureAlgorithm = details.signatureAlgorithm,
            validFrom = details.validFrom,
            validUntil = details.validUntil,
            sha1Fingerprint = details.sha1Fingerprint,
            sha256Fingerprint = details.sha256Fingerprint,
            trustedByOperatingSystem = rootTrustController.isRootCertificateTrusted(),
        )
    }

    override suspend fun installRootCertificate(): TrustInstallationResult = when (
        val result = rootTrustController.installRootCertificate()
    ) {
        InstallationResult.Success -> TrustInstallationResult.Installed
        is InstallationResult.ManualActionRequired -> TrustInstallationResult.ManualActionRequired(
            message = result.message,
            instructions = result.instructions,
        )
        is InstallationResult.Failure -> TrustInstallationResult.Failed(result.message)
    }

    override suspend fun isRootCertificateTrusted(): Boolean = rootTrustController.isRootCertificateTrusted()

    override suspend fun clientCertificates(): List<ClientCertificateSummary> = manager.getClientCertificates().map { value ->
        ClientCertificateSummary(
            alias = value.alias,
            subject = value.subject,
            host = value.host,
            expiration = value.expiration,
            enabled = value.enabled,
            format = ClientCertificateFormat.fromToken(value.format),
            daysUntilExpiration = value.daysUntilExpiration,
            subjectDn = value.subjectDn,
            issuerDn = value.issuerDn,
            serialNumber = value.serialNumber,
            sanList = value.sanList,
            publicKeyAlgorithm = value.publicKeyAlgorithm,
            sha256Fingerprint = value.sha256Fingerprint,
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
