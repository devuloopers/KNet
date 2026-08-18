package com.devuloopers.knet.engine.certificate

import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import com.devuloopers.knet.core.logger.KNetLogger


import com.devuloopers.knet.core.logger.LogTags
import com.devuloopers.knet.engine.certificate.ssl.KNetTrustManagerProvider
import javax.net.ssl.KeyManagerFactory
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter

/**
 * Concrete engine implementation of [CertificateManager].
 *
 * Accepts a pre-loaded or freshly generated [CertificateAuthority] so that the same Root CA
 * keypair is reused across application restarts. When [certificatesDir] is provided, imported
 * client certificates and mTLS rules are persisted to disk in that directory, surviving
 * application restarts without requiring a Room database dependency in the engine layer.
 *
 * Supports multi-format client keypairs including PKCS12 (.p12/.pfx), JKS (.jks), and PEM (.pem/.crt/.cer/.key).
 *
 * @property ca The Certificate Authority whose certificate is used for HTTPS interception and
 *   OS trust store registration. Should be the same instance managed by CertificateRuntimeRepository.
 * @property certificatesDir Directory on disk for persisting client certificates and mTLS rules.
 */
class CertificateManagerImpl(
    private val ca: CertificateAuthority = CertificateAuthority.generate(),
    private val certificatesDir: File? = null
) : CertificateManager {

    private val stateLock = Any()
    private val clientCertificates: MutableList<EngineClientCertificate>
    private val mtlsRules: MutableList<EngineMtlsRule>
    private val keyManagerMap = ConcurrentHashMap<String, KeyManagerFactory>()

    // Paths for persistence files inside certificatesDir.
    private val certsFile: File? = certificatesDir?.let { File(it, "client_certs.json") }
    private val rulesFile: File? = certificatesDir?.let { File(it, "mtls_rules.json") }

    private val clientCertStore = CertificateStore(certsFile)
    private val mtlsRuleStore = CertificateStore(rulesFile)

    init {
        certificatesDir?.let(CertificateFileSecurity::secureDirectory)
        clientCertificates = clientCertStore.loadClientCertificates().toMutableList()
        mtlsRules = mtlsRuleStore.loadMtlsRules().toMutableList()

        // Pre-warm KeyManagerFactory instances for persisted certificates that do not require a passphrase
        clientCertificates.forEach { cert ->
            if (cert.enabled) {
                val candidateFiles = getCandidateKeyFiles(alias = cert.alias, format = cert.format, filePath = cert.filePath)
                for (f in candidateFiles) {
                    if (f.exists()) {
                        val kmf = buildKeyManagerFactory(f, "")
                        if (kmf != null) {
                            keyManagerMap[cert.alias] = kmf
                            break
                        }
                    }
                }
            }
        }
    }

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
        if (result is InstallationResult.Success) {
            KNetTrustManagerProvider.invalidateCache()
        }
        return result is InstallationResult.Success
    }


    override fun isCaTrustedByOs(): Boolean {
        return TrustStoreInstaller.isTrusted(ca.certificate)
    }

    override fun getClientCertificates(): List<EngineClientCertificate> = synchronized(stateLock) {
        clientCertificates.toList()
    }

    override fun importClientCertificate(path: String, alias: String, passphrase: String) {
        val file = File(path)
        if (!file.exists()) {
            throw IllegalArgumentException("Certificate file does not exist: $path")
        }

        // Copy keypair into certificatesDir/keys/ so it persists permanently across sessions
        val savedFile = if (certificatesDir != null) {
            val keysDir = File(certificatesDir, "keys").also(CertificateFileSecurity::secureDirectory)
            val copyTarget = File(
                keysDir,
                CertificateFileSecurity.opaqueIdentityFileName(alias, file.extension),
            )
            file.copyTo(copyTarget, overwrite = true)
            CertificateFileSecurity.secureSecretFile(copyTarget)
            copyTarget
        } else {
            file
        }

        val parsedCert = tryParseCertificate(savedFile, alias, passphrase).copy(
            filePath = savedFile.absolutePath
        )

        val kmf = buildKeyManagerFactory(savedFile, passphrase)
        if (kmf != null) {
            keyManagerMap[alias] = kmf
        }

        synchronized(stateLock) {
            clientCertificates.removeAll { it.alias == alias }
            clientCertificates.add(parsedCert)
            persistClientCertificates()
        }
    }

    override fun exportClientCertificate(alias: String, destinationPath: String) {
        val cert = synchronized(stateLock) {
            clientCertificates.firstOrNull { it.alias == alias }
        } ?: throw IllegalArgumentException("Certificate not found with alias '$alias'")
        val sourceFile = File(cert.filePath)
        if (!sourceFile.exists()) {
            throw IllegalStateException("Source certificate file no longer exists at path '${cert.filePath}'")
        }
        val destDir = File(destinationPath).apply { mkdirs() }
        val destFile = File(
            destDir,
            CertificateFileSecurity.exportIdentityFileName(alias, sourceFile.extension),
        )
        sourceFile.copyTo(destFile, overwrite = true)
        CertificateFileSecurity.secureSecretFile(destFile)
    }

    override fun deleteClientCertificate(alias: String) {
        synchronized(stateLock) {
            clientCertificates.removeAll { it.alias == alias }
            persistClientCertificates()
        }
        keyManagerMap.remove(alias)
    }

    override fun toggleCertificateEnabled(alias: String, enabled: Boolean) {
        synchronized(stateLock) {
            val index = clientCertificates.indexOfFirst { it.alias == alias }
            if (index != -1) {
                val existing = clientCertificates[index]
                clientCertificates[index] = existing.copy(enabled = enabled)
                persistClientCertificates()
            }
        }
    }

    override fun getMtlsRules(): List<EngineMtlsRule> = synchronized(stateLock) {
        mtlsRules.toList()
    }

    override fun addMtlsRule(rule: EngineMtlsRule) {
        synchronized(stateLock) {
            mtlsRules.add(rule)
            persistMtlsRules()
        }
    }

    override fun editMtlsRule(rule: EngineMtlsRule) {
        synchronized(stateLock) {
            val index = mtlsRules.indexOfFirst { it.ruleName == rule.ruleName }
            if (index != -1) {
                mtlsRules[index] = rule
                persistMtlsRules()
            }
        }
    }

    override fun deleteMtlsRule(ruleName: String) {
        synchronized(stateLock) {
            mtlsRules.removeAll { it.ruleName == ruleName }
            persistMtlsRules()
        }
    }

    override fun getKeyManagerFactory(host: String): KeyManagerFactory? {
        val (activeRule, clientCert) = synchronized(stateLock) {
            val rule = mtlsRules.firstOrNull { candidate ->
                candidate.enabled && matchesHostPattern(host, candidate.hostPattern)
            } ?: return null
            rule to clientCertificates.firstOrNull { certificate ->
                certificate.alias == rule.certificateAlias && certificate.enabled
            }
        }

        if (clientCert == null) {
            KNetLogger.warn(LogTags.CERTIFICATE) {
                "[mTLS] Host '$host' matched rule '${activeRule.ruleName}' but cert '${activeRule.certificateAlias}' is not enabled or not found"
            }
            return null
        }

        var kmf = keyManagerMap[clientCert.alias]
        if (kmf == null) {
            val candidateFiles = getCandidateKeyFiles(alias = clientCert.alias, format = clientCert.format, filePath = clientCert.filePath)
            for (f in candidateFiles) {
                if (f.exists()) {
                    kmf = buildKeyManagerFactory(f, "")
                    if (kmf != null) {
                        keyManagerMap[clientCert.alias] = kmf
                        break
                    }
                }
            }
        }

        if (kmf != null) {
            KNetLogger.info(LogTags.CERTIFICATE) {
                "[mTLS] Host '$host' matched rule '${activeRule.ruleName}' -> Attached client certificate '${clientCert.alias}'"
            }
        } else {
            KNetLogger.error(LogTags.CERTIFICATE) {
                "[mTLS] Host '$host' matched rule '${activeRule.ruleName}' but KeyManagerFactory for '${clientCert.alias}' could not be initialized"
            }
        }

        return kmf
    }

    private fun getCandidateKeyFiles(alias: String, format: String = "", filePath: String = ""): List<File> {
        val keysDirectory = certificatesDir?.resolve("keys")?.canonicalFile
        fun ownedKeyFile(candidate: File): File? {
            val canonicalCandidate = runCatching { candidate.canonicalFile }.getOrNull() ?: return null
            return if (
                keysDirectory == null ||
                canonicalCandidate.toPath().startsWith(keysDirectory.toPath())
            ) {
                canonicalCandidate
            } else {
                null
            }
        }

        return listOfNotNull(
            if (filePath.isNotBlank()) ownedKeyFile(File(filePath)) else null,
            if (format.isNotBlank()) keysDirectory?.let {
                ownedKeyFile(File(it, "$alias.${format.lowercase()}"))
            } else {
                null
            },
            keysDirectory?.let { ownedKeyFile(File(it, "$alias.p12")) },
            keysDirectory?.let { ownedKeyFile(File(it, "$alias.pfx")) },
            keysDirectory?.let { ownedKeyFile(File(it, "$alias.pem")) },
            keysDirectory?.let { ownedKeyFile(File(it, "$alias.crt")) },
            keysDirectory?.let { ownedKeyFile(File(it, "$alias.cer")) },
            keysDirectory?.let { ownedKeyFile(File(it, "$alias.jks")) },
        )
    }

    private fun matchesHostPattern(host: String, pattern: String): Boolean {
        val cleanHost = host.lowercase().trim()
        val cleanPattern = pattern.lowercase().trim()

        if (cleanPattern == "*" || cleanPattern.isBlank()) return true
        if (cleanPattern == cleanHost) return true

        if (cleanPattern.startsWith("*.")) {
            val domainSuffix = cleanPattern.substring(2)
            return cleanHost == domainSuffix || cleanHost.endsWith(".$domainSuffix")
        }
        return false
    }

    private fun persistClientCertificates() {
        clientCertStore.persistClientCertificates(clientCertificates)
    }

    private fun persistMtlsRules() {
        mtlsRuleStore.persistMtlsRules(mtlsRules)
    }

    private fun tryParseCertificate(file: File, alias: String, passphrase: String): EngineClientCertificate {
        return try {
            val cert = extractX509Certificate(file, passphrase)

            val subject = cert.subjectX500Principal.name
            val expiryInstant = Instant.fromEpochMilliseconds(cert.notAfter.time)
            val daysUntilExpiry = (expiryInstant - Clock.System.now()).inWholeDays.toInt()
            val formattedExpiry = formatCertificateExpiry(expiryInstant)

            val sans = try {
                cert.subjectAlternativeNames?.mapNotNull { list ->
                    if (list.size >= 2) list[1].toString() else null
                } ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }

            val derivedHost = sans.firstOrNull()
                ?: extractCnFromDn(subject)
                ?: "*"

            val detectedFormat = when (file.extension.lowercase()) {
                "p12", "pfx" -> "PKCS12"
                "jks", "keystore" -> "JKS"
                else -> "PEM"
            }

            EngineClientCertificate(
                alias = alias,
                subject = subject,
                host = derivedHost,
                expiration = formattedExpiry,
                enabled = true,
                format = detectedFormat,
                daysUntilExpiration = daysUntilExpiry,
                subjectDn = subject,
                issuerDn = cert.issuerX500Principal.name,
                serialNumber = cert.serialNumber.toString(16).uppercase().chunked(2).joinToString(":"),
                sanList = sans,
                publicKeyAlgorithm = cert.publicKey.algorithm,
                sha256Fingerprint = getFingerprint(cert, "SHA-256")
            )
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "Failed to parse X.509 certificate from file '${file.name}': ${e.message}",
                e
            )
        }
    }

    private fun extractCnFromDn(dn: String): String? {
        return dn.split(",")
            .map { it.trim() }
            .firstOrNull { it.startsWith("CN=", ignoreCase = true) }
            ?.substringAfter("=")
            ?.trim()
    }

    private fun extractX509Certificate(file: File, passphrase: String = ""): X509Certificate {
        return if (file.extension.lowercase() in listOf("p12", "pfx")) {
            val candidatePasswords = listOf(passphrase, "", "badssl.com").distinct()
            val ks = loadKeyStoreWithFallback("PKCS12", file, candidatePasswords)
            val certAlias = ks.aliases().nextElement()
            ks.getCertificate(certAlias) as X509Certificate
        } else if (file.extension.lowercase() in listOf("jks", "keystore")) {
            val candidatePasswords = listOf(passphrase, "").distinct()
            val ks = loadKeyStoreWithFallback("JKS", file, candidatePasswords)
            val certAlias = ks.aliases().nextElement()
            ks.getCertificate(certAlias) as X509Certificate
        } else {
            val cf = CertificateFactory.getInstance("X.509")
            FileInputStream(file).use { cf.generateCertificate(it) as X509Certificate }
        }
    }

    private fun loadKeyStoreWithFallback(type: String, file: File, candidatePasswords: List<String>): KeyStore {
        val ks = KeyStore.getInstance(type)
        var loaded = false
        var lastException: Exception? = null
        for (pass in candidatePasswords) {
            try {
                FileInputStream(file).use { ks.load(it, pass.toCharArray()) }
                loaded = true
                break
            } catch (e: Exception) {
                lastException = e
            }
        }
        if (!loaded && lastException != null) {
            throw lastException
        }
        return ks
    }

    private fun buildKeyManagerFactory(file: File, passphrase: String = ""): KeyManagerFactory? {
        val ext = file.extension.lowercase()
        return when (ext) {
            "p12", "pfx" -> loadPkcs12KeyManagerFactory(file, passphrase)
            "jks", "keystore" -> loadJksKeyManagerFactory(file, passphrase)
            "pem", "crt", "cer", "key" -> loadPemKeyManagerFactory(file, passphrase)
                ?: loadPkcs12KeyManagerFactory(file, passphrase)

            else -> {
                loadPkcs12KeyManagerFactory(file, passphrase)
                    ?: loadPemKeyManagerFactory(file, passphrase)
                    ?: loadJksKeyManagerFactory(file, passphrase)
            }
        }
    }

    private fun loadPkcs12KeyManagerFactory(file: File, passphrase: String = ""): KeyManagerFactory? {
        val candidatePasswords = listOf(passphrase, "", "badssl.com").distinct()
        for (pass in candidatePasswords) {
            try {
                val ks = KeyStore.getInstance("PKCS12")
                FileInputStream(file).use { ks.load(it, pass.toCharArray()) }
                val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                kmf.init(ks, pass.toCharArray())
                return kmf
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun loadJksKeyManagerFactory(file: File, passphrase: String = ""): KeyManagerFactory? {
        val candidatePasswords = listOf(passphrase, "").distinct()
        for (pass in candidatePasswords) {
            try {
                val ks = KeyStore.getInstance("JKS")
                FileInputStream(file).use { ks.load(it, pass.toCharArray()) }
                val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                kmf.init(ks, pass.toCharArray())
                return kmf
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun loadPemKeyManagerFactory(file: File, passphrase: String = ""): KeyManagerFactory? {
        return try {
            val parser = PEMParser(file.reader())
            var privateKey: java.security.PrivateKey? = null
            val certs = mutableListOf<X509Certificate>()
            val keyConverter = JcaPEMKeyConverter()
            val certConverter = JcaX509CertificateConverter()

            var obj = parser.readObject()
            while (obj != null) {
                when (obj) {
                    is PEMKeyPair -> {
                        privateKey = keyConverter.getKeyPair(obj).private
                    }

                    is PrivateKeyInfo -> {
                        privateKey = keyConverter.getPrivateKey(obj)
                    }

                    is X509CertificateHolder -> {
                        certs.add(certConverter.getCertificate(obj))
                    }
                }
                obj = parser.readObject()
            }
            parser.close()

            if (privateKey != null && certs.isNotEmpty()) {
                val ks = KeyStore.getInstance(KeyStore.getDefaultType())
                ks.load(null, null)
                ks.setKeyEntry("pem-client", privateKey, passphrase.toCharArray(), certs.toTypedArray())
                val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                kmf.init(ks, passphrase.toCharArray())
                kmf
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getFingerprint(cert: X509Certificate, algorithm: String): String {
        val md = MessageDigest.getInstance(algorithm)
        val der = cert.encoded
        val digest = md.digest(der)
        return digest.joinToString(":") { "%02X".format(it) }
    }

    private fun formatCertificateExpiry(expiryInstant: Instant): String {
        val dateTime = expiryInstant.toLocalDateTime(TimeZone.currentSystemDefault())
        return buildString(capacity = 19) {
            append(dateTime.date)
            append(' ')
            append(dateTime.hour.fixedWidth(2))
            append(':')
            append(dateTime.minute.fixedWidth(2))
            append(':')
            append(dateTime.second.fixedWidth(2))
        }
    }

    private fun Int.fixedWidth(width: Int): String = toString().padStart(width, '0')
}
