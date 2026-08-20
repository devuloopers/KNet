package com.devuloopers.knet.engine.certificate

import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.core.logger.LogTags
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import javax.net.ssl.KeyManagerFactory
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import kotlin.concurrent.withLock
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Concrete engine implementation of [CertificateManager].
 *
 * Accepts a pre-loaded or freshly generated [CertificateAuthority] so that the same Root CA
 * keypair is reused across application restarts. Imported key material is normalized into
 * [identityDirectory], while the caller-provided [configurationStore] owns metadata persistence.
 *
 * Supports multi-format client keypairs including PKCS12 (.p12/.pfx), JKS (.jks), and PEM (.pem/.crt/.cer/.key).
 *
 * @property ca The Certificate Authority whose certificate is used for HTTPS interception.
 * @property identityDirectory Owner-only directory for normalized client private-key material.
 * @property configurationStore Atomic persistence boundary for certificate and rule metadata.
 */
class CertificateManagerImpl(
    private val ca: CertificateAuthority = CertificateAuthority.generate(),
    private val identityDirectory: File? = null,
    private val configurationStore: CertificateConfigurationStore = VolatileCertificateConfigurationStore,
) : CertificateManager {

    private val stateLock = ReentrantLock()
    private val clientCertificates: MutableList<EngineClientCertificate>
    private val mtlsRules: MutableList<EngineMtlsRule>
    private val keyManagerMap = ConcurrentHashMap<String, KeyManagerFactory>()

    init {
        identityDirectory?.let { directory ->
            check(CertificateFileSecurity.secureDirectory(directory)) {
                "Unable to secure certificate directory '${directory.absolutePath}'."
            }
        }
        val initialConfiguration = configurationStore.load()
        clientCertificates = initialConfiguration.clientCertificates.toMutableList()
        mtlsRules = initialConfiguration.mtlsRules.toMutableList()

        // Imported identities are normalized to owner-only PKCS#12 files with an empty internal password.
        // The source passphrase is only held for the duration of import and is never persisted.
        var configurationRepaired = false
        clientCertificates.forEachIndexed { index, cert ->
            if (cert.enabled) {
                val identityFile = ownedIdentityFile(cert.filePath)
                if (identityFile?.exists() == true) {
                    runCatching { loadNormalizedKeyManagerFactory(identityFile) }
                        .onSuccess { keyManagerMap[cert.alias] = it }
                        .onFailure { error ->
                            clientCertificates[index] = cert.copy(enabled = false)
                            configurationRepaired = true
                            KNetLogger.warn(LogTags.CERTIFICATE) {
                                "[mTLS] Disabled unreadable persisted identity '${cert.alias}': ${error.message}"
                            }
                        }
                } else {
                    clientCertificates[index] = cert.copy(enabled = false)
                    configurationRepaired = true
                    KNetLogger.warn(LogTags.CERTIFICATE) {
                        "[mTLS] Disabled identity '${cert.alias}' because its private-key file is missing."
                    }
                }
            }
        }
        if (configurationRepaired) configurationStore.persist(configuration())
        removeUnreferencedIdentityFiles()
    }

    override fun getAuthorityDetails(): CertificateAuthorityDetails = CertificateAuthorityDetails(
        status = when {
            ca.certificate.notAfter.time < Clock.System.now().toEpochMilliseconds() -> CertificateAuthorityStatus.EXPIRED
            runCatching(ca::validate).isFailure -> CertificateAuthorityStatus.INVALID
            else -> CertificateAuthorityStatus.AVAILABLE
        },
        subject = ca.certificate.subjectX500Principal.name,
        issuer = ca.certificate.issuerX500Principal.name,
        serialNumber = ca.certificate.serialNumber.toString(16).uppercase().chunked(2).joinToString(":"),
        signatureAlgorithm = ca.certificate.sigAlgName,
        validFrom = Instant.fromEpochMilliseconds(ca.certificate.notBefore.time).toString(),
        validUntil = Instant.fromEpochMilliseconds(ca.certificate.notAfter.time).toString(),
        sha1Fingerprint = getFingerprint(ca.certificate, "SHA-1"),
        sha256Fingerprint = getFingerprint(ca.certificate, "SHA-256"),
    )

    override fun getClientCertificates(): List<EngineClientCertificate> = stateLock.withLock {
        clientCertificates.toList()
    }

    override fun importClientCertificate(path: String, alias: String, passphrase: String) {
        val normalizedAlias = alias.trim()
        require(normalizedAlias.isNotBlank()) { "Certificate alias must not be blank." }
        val sourceFile = File(path)
        if (!sourceFile.isFile) {
            throw IllegalArgumentException("Certificate file does not exist: $path")
        }
        val loadedIdentity = loadClientIdentity(sourceFile, passphrase)
        val savedFile = persistNormalizedIdentity(normalizedAlias, loadedIdentity)
        val parsedCertificate = describeCertificate(
            certificate = loadedIdentity.certificate,
            alias = normalizedAlias,
            sourceFormat = loadedIdentity.sourceFormat,
            filePath = savedFile.absolutePath,
        )
        val keyManagerFactory = loadNormalizedKeyManagerFactory(savedFile)

        stateLock.withLock {
            val previous = clientCertificates.toList()
            val conflictingAlias = previous.firstOrNull {
                it.alias.equals(normalizedAlias, ignoreCase = true) && it.alias != normalizedAlias
            }
            if (conflictingAlias != null) {
                savedFile.delete()
                throw IllegalArgumentException(
                    "Certificate alias '$normalizedAlias' conflicts with existing alias '${conflictingAlias.alias}'."
                )
            }
            val previousIdentityFile = previous.firstOrNull { it.alias == normalizedAlias }
                ?.filePath
                ?.let(::ownedIdentityFile)
            val updated = previous.filterNot { it.alias == normalizedAlias } + parsedCertificate
            try {
                configurationStore.persist(configuration(clientCertificates = updated))
                clientCertificates.clear()
                clientCertificates.addAll(updated)
                keyManagerMap[normalizedAlias] = keyManagerFactory
                if (previousIdentityFile != null && previousIdentityFile != savedFile) {
                    previousIdentityFile.delete()
                }
            } catch (error: Exception) {
                if (savedFile != sourceFile) savedFile.delete()
                throw IllegalStateException("Unable to persist imported certificate '$normalizedAlias'.", error)
            }
        }
    }

    override fun exportClientCertificate(alias: String, destinationPath: String) {
        val cert = stateLock.withLock {
            clientCertificates.firstOrNull { it.alias == alias }
        } ?: throw IllegalArgumentException("Certificate not found with alias '$alias'")
        val sourceFile = File(cert.filePath)
        if (!sourceFile.exists()) {
            throw IllegalStateException("Source certificate file no longer exists at path '${cert.filePath}'")
        }
        val destDir = File(destinationPath)
        check((destDir.isDirectory || destDir.mkdirs()) && destDir.isDirectory) {
            "Unable to create export directory '${destDir.absolutePath}'."
        }
        val destFile = File(
            destDir,
            CertificateFileSecurity.exportIdentityFileName(alias, sourceFile.extension),
        )
        sourceFile.copyTo(destFile, overwrite = true)
        check(CertificateFileSecurity.secureSecretFile(destFile)) {
            "Unable to secure exported identity '${destFile.absolutePath}'."
        }
    }

    override fun deleteClientCertificate(alias: String) {
        stateLock.withLock {
            val certificate = clientCertificates.firstOrNull { it.alias == alias }
                ?: return
            val updatedCertificates = clientCertificates.filterNot { it.alias == alias }
            val updatedRules = mtlsRules.filterNot { it.certificateAlias == alias }
            val identityFile = ownedIdentityFile(certificate.filePath)
            val tombstone = identityFile?.takeIf(File::exists)?.let { file ->
                File(file.parentFile, "${file.name}.deleting").also { destination ->
                    Files.move(file.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            }
            try {
                configurationStore.persist(
                    CertificateConfiguration(
                        clientCertificates = updatedCertificates,
                        mtlsRules = updatedRules,
                    )
                )
            } catch (error: Exception) {
                tombstone?.takeIf(File::exists)?.let { backup ->
                    Files.move(backup.toPath(), identityFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                throw IllegalStateException("Unable to delete certificate '$alias'.", error)
            }
            tombstone?.delete()
            clientCertificates.clear()
            clientCertificates.addAll(updatedCertificates)
            mtlsRules.clear()
            mtlsRules.addAll(updatedRules)
            keyManagerMap.remove(alias)
        }
    }

    override fun toggleCertificateEnabled(alias: String, enabled: Boolean) {
        stateLock.withLock {
            val index = clientCertificates.indexOfFirst { it.alias == alias }
            if (index != -1) {
                val existing = clientCertificates[index]
                val reloadedKeyManager = if (enabled) {
                    val identityFile = ownedIdentityFile(existing.filePath)
                    require(identityFile?.isFile == true) {
                        "Private-key material for certificate '$alias' is missing. Import the identity again."
                    }
                    runCatching { loadNormalizedKeyManagerFactory(identityFile) }
                        .getOrElse { error ->
                            throw IllegalStateException(
                                "Private-key material for certificate '$alias' cannot be loaded. Import the identity again.",
                                error,
                            )
                        }
                } else {
                    null
                }
                val updated = clientCertificates.toMutableList().apply {
                    this[index] = existing.copy(enabled = enabled)
                }
                configurationStore.persist(configuration(clientCertificates = updated))
                clientCertificates.clear()
                clientCertificates.addAll(updated)
                if (reloadedKeyManager != null) keyManagerMap[alias] = reloadedKeyManager
            }
        }
    }

    override fun getMtlsRules(): List<EngineMtlsRule> = stateLock.withLock {
        mtlsRules.toList()
    }

    override fun addMtlsRule(rule: EngineMtlsRule) {
        stateLock.withLock {
            val validated = validateRule(rule)
            require(mtlsRules.none { it.ruleName.equals(validated.ruleName, ignoreCase = true) }) {
                "An mTLS rule named '${validated.ruleName}' already exists."
            }
            require(mtlsRules.none { it.hostPattern.equals(validated.hostPattern, ignoreCase = true) }) {
                "An mTLS rule already handles '${validated.hostPattern}'."
            }
            val updated = mtlsRules + validated
            configurationStore.persist(configuration(mtlsRules = updated))
            mtlsRules.clear()
            mtlsRules.addAll(updated)
        }
    }

    override fun editMtlsRule(rule: EngineMtlsRule) {
        stateLock.withLock {
            val index = mtlsRules.indexOfFirst { it.ruleName == rule.ruleName }
            require(index != -1) { "mTLS rule '${rule.ruleName}' was not found." }
            val validated = validateRule(rule)
            require(mtlsRules.withIndex().none { (candidateIndex, candidate) ->
                candidateIndex != index && candidate.hostPattern.equals(validated.hostPattern, ignoreCase = true)
            }) {
                "An mTLS rule already handles '${validated.hostPattern}'."
            }
            val updated = mtlsRules.toMutableList().apply { this[index] = validated }
            configurationStore.persist(configuration(mtlsRules = updated))
            mtlsRules.clear()
            mtlsRules.addAll(updated)
        }
    }

    override fun deleteMtlsRule(ruleName: String) {
        stateLock.withLock {
            val updated = mtlsRules.filterNot { it.ruleName == ruleName }
            configurationStore.persist(configuration(mtlsRules = updated))
            mtlsRules.clear()
            mtlsRules.addAll(updated)
        }
    }

    override fun getKeyManagerFactory(host: String): KeyManagerFactory? {
        val (activeRule, clientCert) = stateLock.withLock {
            val rule = mtlsRules.asSequence()
                .filter { candidate -> candidate.enabled && matchesHostPattern(host, candidate.hostPattern) }
                .maxByOrNull { candidate -> hostPatternSpecificity(candidate.hostPattern) }
                ?: return null
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
            val identityFile = ownedIdentityFile(clientCert.filePath)
            if (identityFile?.exists() == true) {
                kmf = runCatching { loadNormalizedKeyManagerFactory(identityFile) }.getOrNull()
                if (kmf != null) keyManagerMap[clientCert.alias] = kmf
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

    private fun ownedIdentityFile(filePath: String): File? {
        val keysDirectory = identityDirectory?.resolve("keys")?.canonicalFile
        if (filePath.isBlank()) return null
        val candidate = runCatching { File(filePath).canonicalFile }.getOrNull() ?: return null
        return if (keysDirectory == null || candidate.toPath().startsWith(keysDirectory.toPath())) candidate else null
    }

    /** Removes interrupted-deletion/replacement files that no persisted identity owns. */
    private fun removeUnreferencedIdentityFiles() {
        val keysDirectory = identityDirectory?.resolve("keys")?.takeIf(File::isDirectory) ?: return
        val referencedPaths = clientCertificates.mapNotNull { certificate ->
            ownedIdentityFile(certificate.filePath)?.canonicalPath
        }.toSet()
        keysDirectory.listFiles().orEmpty()
            .asSequence()
            .filter(File::isFile)
            .filterNot { file -> file.canonicalPath in referencedPaths }
            .forEach { file ->
                if (!file.delete()) {
                    KNetLogger.warn(LogTags.CERTIFICATE) {
                        "[mTLS] Unable to remove unreferenced identity material '${file.absolutePath}'."
                    }
                }
            }
    }

    private fun matchesHostPattern(host: String, pattern: String): Boolean {
        val cleanHost = host.lowercase().trim()
        val cleanPattern = pattern.lowercase().trim()

        if (cleanPattern == "*") return true
        if (cleanPattern.isBlank()) return false
        if (cleanPattern == cleanHost) return true

        if (cleanPattern.startsWith("*.")) {
            val domainSuffix = cleanPattern.substring(2)
            return cleanHost.endsWith(".$domainSuffix") && cleanHost.length > domainSuffix.length + 1
        }
        return false
    }

    private fun hostPatternSpecificity(pattern: String): Int = when {
        pattern == "*" || pattern.isBlank() -> 0
        pattern.startsWith("*.") -> 1_000_000 + pattern.length
        else -> 2_000_000 + pattern.length
    }

    private fun validateRule(rule: EngineMtlsRule): EngineMtlsRule {
        val ruleName = rule.ruleName.trim()
        val hostPattern = rule.hostPattern.trim().lowercase()
        val certificateAlias = rule.certificateAlias.trim()
        require(ruleName.isNotBlank()) { "Rule name must not be blank." }
        require(certificateAlias.isNotBlank()) { "A client certificate must be selected." }
        require(clientCertificates.any { it.alias == certificateAlias }) {
            "Client certificate '$certificateAlias' was not found."
        }
        require(hostPattern == "*" || HOST_PATTERN.matches(hostPattern)) {
            "Use a hostname such as api.example.com, *.example.com, or *."
        }
        return rule.copy(ruleName = ruleName, hostPattern = hostPattern, certificateAlias = certificateAlias)
    }

    private fun describeCertificate(
        certificate: X509Certificate,
        alias: String,
        sourceFormat: String,
        filePath: String,
    ): EngineClientCertificate {
        val subject = certificate.subjectX500Principal.name
        val expiryInstant = Instant.fromEpochMilliseconds(certificate.notAfter.time)
        val daysUntilExpiry = (expiryInstant - Clock.System.now()).inWholeDays.toInt()
        val sans = runCatching {
            certificate.subjectAlternativeNames?.mapNotNull { entry ->
                if (entry.size >= 2) entry[1].toString() else null
            }.orEmpty()
        }.getOrDefault(emptyList())
        return EngineClientCertificate(
            alias = alias,
            subject = subject,
            host = sans.firstOrNull() ?: extractCnFromDn(subject) ?: "*",
            expiration = formatCertificateExpiry(expiryInstant),
            enabled = true,
            format = sourceFormat,
            daysUntilExpiration = daysUntilExpiry,
            subjectDn = subject,
            issuerDn = certificate.issuerX500Principal.name,
            serialNumber = certificate.serialNumber.toString(16).uppercase().chunked(2).joinToString(":"),
            sanList = sans,
            publicKeyAlgorithm = certificate.publicKey.algorithm,
            sha256Fingerprint = getFingerprint(certificate, "SHA-256"),
            filePath = filePath,
        )
    }

    private fun extractCnFromDn(dn: String): String? {
        return dn.split(",")
            .map { it.trim() }
            .firstOrNull { it.startsWith("CN=", ignoreCase = true) }
            ?.substringAfter("=")
            ?.trim()
    }

    private fun loadClientIdentity(file: File, passphrase: String): LoadedClientIdentity = try {
        when (file.extension.lowercase()) {
            "p12", "pfx" -> loadKeyStoreIdentity(file, "PKCS12", passphrase, "PKCS12")
            "jks", "keystore" -> loadKeyStoreIdentity(file, "JKS", passphrase, "JKS")
            "pem", "crt", "cer", "key" -> loadPemIdentity(file)
            else -> throw IllegalArgumentException("Unsupported client certificate format '.${file.extension}'.")
        }
    } catch (error: Exception) {
        throw IllegalArgumentException(
            "Unable to import client identity '${file.name}'. Verify that it contains a private key, certificate chain, and the correct passphrase.",
            error,
        )
    }

    private fun loadKeyStoreIdentity(
        file: File,
        storeType: String,
        passphrase: String,
        sourceFormat: String,
    ): LoadedClientIdentity {
        val password = passphrase.toCharArray()
        val sourceStore = KeyStore.getInstance(storeType)
        FileInputStream(file).use { input -> sourceStore.load(input, password) }
        val keyAlias = sourceStore.aliases().asSequence().firstOrNull(sourceStore::isKeyEntry)
            ?: throw IllegalArgumentException("The selected keystore does not contain a private-key entry.")
        val privateKey = sourceStore.getKey(keyAlias, password) as? PrivateKey
            ?: throw IllegalArgumentException("The selected keystore entry does not contain a private key.")
        val chain = sourceStore.getCertificateChain(keyAlias)
            ?: throw IllegalArgumentException("The selected keystore entry does not contain a certificate chain.")
        val leaf = chain.firstOrNull() as? X509Certificate
            ?: throw IllegalArgumentException("The selected keystore entry does not contain an X.509 certificate.")
        return LoadedClientIdentity(normalizedKeyStore(privateKey, chain), leaf, sourceFormat)
    }

    private fun loadPemIdentity(file: File): LoadedClientIdentity {
        var privateKey: PrivateKey? = null
        val certificates = mutableListOf<X509Certificate>()
        val keyConverter = JcaPEMKeyConverter()
        val certificateConverter = JcaX509CertificateConverter()
        PEMParser(file.reader()).use { parser ->
            var value = parser.readObject()
            while (value != null) {
                when (value) {
                    is PEMKeyPair -> privateKey = keyConverter.getKeyPair(value).private
                    is PrivateKeyInfo -> privateKey = keyConverter.getPrivateKey(value)
                    is X509CertificateHolder -> certificates += certificateConverter.getCertificate(value)
                }
                value = parser.readObject()
            }
        }
        val key = privateKey ?: throw IllegalArgumentException(
            "PEM client identities must include an unencrypted private key in the selected file."
        )
        val leaf = certificates.firstOrNull()
            ?: throw IllegalArgumentException("PEM client identities must include an X.509 certificate chain.")
        return LoadedClientIdentity(normalizedKeyStore(key, certificates.toTypedArray()), leaf, "PEM")
    }

    private fun normalizedKeyStore(
        privateKey: PrivateKey,
        chain: Array<out java.security.cert.Certificate>,
    ): KeyStore = KeyStore.getInstance("PKCS12").apply {
        load(null, null)
        setKeyEntry(NORMALIZED_KEY_ALIAS, privateKey, INTERNAL_PASSWORD, chain)
    }

    private fun persistNormalizedIdentity(alias: String, identity: LoadedClientIdentity): File {
        val destination = if (identityDirectory == null) {
            File.createTempFile("knet_client_", ".p12").apply { deleteOnExit() }
        } else {
            val keysDirectory = File(identityDirectory, "keys")
            check(CertificateFileSecurity.secureDirectory(keysDirectory)) {
                "Unable to secure client identity directory '${keysDirectory.absolutePath}'."
            }
            File(
                keysDirectory,
                CertificateFileSecurity.opaqueIdentityFileName("$alias:${System.nanoTime()}", "p12"),
            )
        }
        FileOutputStream(destination).use { output -> identity.keyStore.store(output, INTERNAL_PASSWORD) }
        check(CertificateFileSecurity.secureSecretFile(destination)) {
            "Unable to apply owner-only permissions to '${destination.absolutePath}'."
        }
        return destination
    }

    private fun loadNormalizedKeyManagerFactory(file: File): KeyManagerFactory {
        val keyStore = KeyStore.getInstance("PKCS12")
        FileInputStream(file).use { input -> keyStore.load(input, INTERNAL_PASSWORD) }
        return KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, INTERNAL_PASSWORD)
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

    private fun configuration(
        clientCertificates: List<EngineClientCertificate> = this.clientCertificates.toList(),
        mtlsRules: List<EngineMtlsRule> = this.mtlsRules.toList(),
    ): CertificateConfiguration = CertificateConfiguration(clientCertificates, mtlsRules)

    private data class LoadedClientIdentity(
        val keyStore: KeyStore,
        val certificate: X509Certificate,
        val sourceFormat: String,
    )

    private companion object {
        val INTERNAL_PASSWORD = charArrayOf()
        const val NORMALIZED_KEY_ALIAS = "knet-client-identity"
        val HOST_PATTERN = Regex(
            """^(?:\*\.)?(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)*[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$"""
        )
    }
}
