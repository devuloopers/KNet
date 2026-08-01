package com.devuloopers.knet.engine.certificate

import java.security.MessageDigest
import java.security.cert.X509Certificate

/**
 * Concrete engine implementation of [CertificateManager].
 *
 * It generates and wraps a self-signed [CertificateAuthority] and delegates OS registration
 * to [TrustStoreInstaller].
 */
public class CertificateManagerImpl : CertificateManager {

    private val ca: CertificateAuthority by lazy {
        CertificateAuthority.generate()
    }

    private val clientCertificates: MutableList<EngineClientCertificate> = mutableListOf()
    private val mtlsRules: MutableList<EngineMtlsRule> = mutableListOf()

    override fun getCaStatus(): String = "AVAILABLE"

    override fun getCaSubject(): String = ca.certificate.subjectX500Principal.name

    override fun getCaIssuer(): String = ca.certificate.issuerX500Principal.name

    override fun getCaSerialNumber(): String {
        return ca.certificate.serialNumber.toString(16).uppercase().chunked(2).joinToString(":")
    }

    override fun getCaSignatureAlgorithm(): String = ca.certificate.sigAlgName

    override fun getCaValidFrom(): String = ca.certificate.notBefore.toString()

    override fun getCaValidUntil(): String = ca.certificate.notAfter.toString()

    override fun getCaSha1Fingerprint(): String = getFingerprint(ca.certificate, "SHA-1")

    override fun getCaSha256Fingerprint(): String = getFingerprint(ca.certificate, "SHA-256")

    override fun installRootCertificate(): Boolean {
        val result = TrustStoreInstaller.install(ca.certificate)
        return result is InstallationResult.Success
    }

    override fun getClientCertificates(): List<EngineClientCertificate> = clientCertificates.toList()

    override fun importClientCertificate(path: String, alias: String) {
        val newCert = EngineClientCertificate(
            alias = alias,
            subject = "CN=$alias, O=Client, L=Local",
            host = "*",
            expiration = "2029-12-31"
        )
        clientCertificates.add(newCert)
    }

    override fun exportClientCertificate(alias: String, destinationPath: String) {
        // mock export simulation
    }

    override fun deleteClientCertificate(alias: String) {
        clientCertificates.removeAll { it.alias == alias }
    }

    override fun getMtlsRules(): List<EngineMtlsRule> = mtlsRules.toList()

    override fun addMtlsRule(rule: EngineMtlsRule) {
        mtlsRules.add(rule)
    }

    override fun editMtlsRule(rule: EngineMtlsRule) {
        val index = mtlsRules.indexOfFirst { it.ruleName == rule.ruleName }
        if (index != -1) {
            mtlsRules[index] = rule
        }
    }

    override fun deleteMtlsRule(ruleName: String) {
        mtlsRules.removeAll { it.ruleName == ruleName }
    }

    /**
     * Calculates the digest fingerprint representing the certificate.
     */
    private fun getFingerprint(cert: X509Certificate, algorithm: String): String {
        val messageDigest = MessageDigest.getInstance(algorithm)
        val der = cert.encoded
        val digest = messageDigest.digest(der)
        return digest.joinToString(":") { String.format("%02X", it) }
    }
}
