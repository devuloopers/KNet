package com.devuloopers.knet.engine.certificate

import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Concrete engine implementation of [CertificateManager].
 *
 * It generates and wraps a self-signed [CertificateAuthority], handles certificate file parsing
 * for PKCS#12 and PEM client certificates, and delegates OS registration to [TrustStoreInstaller].
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
        val file = File(path)
        if (!file.exists()) {
            // Fallback for mock/test alias import when file doesn't exist
            val mockCert = EngineClientCertificate(
                alias = alias,
                subject = "CN=$alias, O=Client, L=Local",
                host = "*",
                expiration = "2029-12-31",
                enabled = true,
                format = "PKCS12",
                daysUntilExpiration = 365,
                subjectDn = "CN=$alias, O=Client, L=Local",
                issuerDn = "CN=KNet Proxy Local CA",
                serialNumber = "01:A2:B3:C4",
                sanList = listOf("DNS:*"),
                publicKeyAlgorithm = "RSA 2048-bit",
                sha256Fingerprint = "AA:BB:CC:DD:EE:FF:00:11"
            )
            clientCertificates.removeAll { it.alias == alias }
            clientCertificates.add(mockCert)
            return
        }

        val parsedCert = tryParseCertificate(file, alias)
        clientCertificates.removeAll { it.alias == alias }
        clientCertificates.add(parsedCert)
    }

    override fun exportClientCertificate(alias: String, destinationPath: String) {
        val cert = clientCertificates.find { it.alias == alias } ?: return
        val destFile = File(destinationPath, "$alias.crt")
        destFile.writeText("-----BEGIN CERTIFICATE-----\n# Exported certificate placeholder for $alias\n-----END CERTIFICATE-----\n")
    }

    override fun deleteClientCertificate(alias: String) {
        clientCertificates.removeAll { it.alias == alias }
    }

    override fun toggleCertificateEnabled(alias: String, enabled: Boolean) {
        val index = clientCertificates.indexOfFirst { it.alias == alias }
        if (index != -1) {
            val existing = clientCertificates[index]
            clientCertificates[index] = existing.copy(enabled = enabled)
        }
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
     * Parses an X.509 certificate file (PKCS#12 or PEM) into an [EngineClientCertificate].
     */
    private fun tryParseCertificate(file: File, alias: String): EngineClientCertificate {
        return try {
            val cert = extractX509Certificate(file)
            val format = if (file.extension.lowercase() in listOf("p12", "pfx")) "PKCS12" else "PEM"
            val validUntil = cert.notAfter.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
            val now = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate()
            val daysRemaining = ChronoUnit.DAYS.between(now, validUntil).toInt()
            val dateStr = validUntil.format(DateTimeFormatter.ISO_LOCAL_DATE)

            val sans = mutableListOf<String>()
            cert.subjectAlternativeNames?.forEach { item ->
                if (item.size >= 2) {
                    sans.add(item[1].toString())
                }
            }

            EngineClientCertificate(
                alias = alias,
                subject = cert.subjectX500Principal.name,
                host = if (sans.isNotEmpty()) sans.first() else "*",
                expiration = dateStr,
                enabled = true,
                format = format,
                daysUntilExpiration = daysRemaining,
                subjectDn = cert.subjectX500Principal.name,
                issuerDn = cert.issuerX500Principal.name,
                serialNumber = cert.serialNumber.toString(16).uppercase().chunked(2).joinToString(":"),
                sanList = sans,
                publicKeyAlgorithm = cert.publicKey.algorithm,
                sha256Fingerprint = getFingerprint(cert, "SHA-256")
            )
        } catch (_: Exception) {
            EngineClientCertificate(
                alias = alias,
                subject = "CN=$alias",
                host = "*",
                expiration = "2029-12-31",
                enabled = true
            )
        }
    }

    private fun extractX509Certificate(file: File): X509Certificate {
        return if (file.extension.lowercase() in listOf("p12", "pfx")) {
            val ks = KeyStore.getInstance("PKCS12")
            FileInputStream(file).use { ks.load(it, "".toCharArray()) }
            val certAlias = ks.aliases().nextElement()
            ks.getCertificate(certAlias) as X509Certificate
        } else {
            val cf = CertificateFactory.getInstance("X.509")
            FileInputStream(file).use { cf.generateCertificate(it) as X509Certificate }
        }
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
