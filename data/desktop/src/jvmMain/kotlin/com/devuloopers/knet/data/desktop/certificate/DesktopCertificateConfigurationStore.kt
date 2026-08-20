package com.devuloopers.knet.data.desktop.certificate

import com.devuloopers.knet.application.port.certificate.ClientCertificateFormat
import com.devuloopers.knet.engine.certificate.CertificateConfiguration
import com.devuloopers.knet.engine.certificate.CertificateConfigurationStore
import com.devuloopers.knet.engine.certificate.CertificateFileSecurity
import com.devuloopers.knet.engine.certificate.EngineClientCertificate
import com.devuloopers.knet.engine.certificate.EngineMtlsRule
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Atomic desktop JSON adapter for the certificate engine's configuration-store port. */
internal class DesktopCertificateConfigurationStore(
    private val file: File,
) : CertificateConfigurationStore {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun load(): CertificateConfiguration {
        if (!file.exists()) return CertificateConfiguration()
        check(file.isFile) { "Certificate configuration path is not a file: '${file.absolutePath}'." }
        return try {
            json.decodeFromString<CertificateConfigurationDocument>(file.readText()).toEngine()
        } catch (error: Exception) {
            throw IllegalStateException(
                "Unable to read certificate configuration '${file.absolutePath}'. The file was left untouched.",
                error,
            )
        }
    }

    override fun persist(configuration: CertificateConfiguration) {
        val parent = requireNotNull(file.parentFile) {
            "Certificate configuration must have a parent directory."
        }
        check(CertificateFileSecurity.secureDirectory(parent)) {
            "Unable to secure certificate configuration directory '${parent.absolutePath}'."
        }
        val temporary = File.createTempFile("certificate_configuration_", ".tmp", parent)
        try {
            temporary.writeText(json.encodeToString(CertificateConfigurationDocument.from(configuration)))
            check(CertificateFileSecurity.secureSecretFile(temporary)) {
                "Unable to secure temporary certificate configuration '${temporary.absolutePath}'."
            }
            try {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
            check(CertificateFileSecurity.secureSecretFile(file)) {
                "Unable to secure certificate configuration '${file.absolutePath}'."
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }
}

@Serializable
private data class CertificateConfigurationDocument(
    val version: Int = CURRENT_VERSION,
    val clientCertificates: List<ClientCertificateDocument> = emptyList(),
    val mtlsRules: List<MtlsRuleDocument> = emptyList(),
) {
    fun toEngine(): CertificateConfiguration {
        check(version == CURRENT_VERSION) { "Unsupported certificate configuration version '$version'." }
        return CertificateConfiguration(
            clientCertificates = clientCertificates.map(ClientCertificateDocument::toEngine),
            mtlsRules = mtlsRules.map(MtlsRuleDocument::toEngine),
        )
    }

    companion object {
        private const val CURRENT_VERSION = 1

        fun from(configuration: CertificateConfiguration): CertificateConfigurationDocument =
            CertificateConfigurationDocument(
                clientCertificates = configuration.clientCertificates.map(ClientCertificateDocument::from),
                mtlsRules = configuration.mtlsRules.map(MtlsRuleDocument::from),
            )
    }
}

@Serializable
private data class ClientCertificateDocument(
    val alias: String,
    val subject: String,
    val host: String,
    val expiration: String,
    val enabled: Boolean,
    val format: String,
    val daysUntilExpiration: Int,
    val subjectDn: String,
    val issuerDn: String,
    val serialNumber: String,
    val sanList: List<String>,
    val publicKeyAlgorithm: String,
    val sha256Fingerprint: String,
    val filePath: String,
) {
    fun toEngine(): EngineClientCertificate {
        ClientCertificateFormat.fromToken(format)
        return EngineClientCertificate(
            alias = alias,
            subject = subject,
            host = host,
            expiration = expiration,
            enabled = enabled,
            format = format,
            daysUntilExpiration = daysUntilExpiration,
            subjectDn = subjectDn,
            issuerDn = issuerDn,
            serialNumber = serialNumber,
            sanList = sanList,
            publicKeyAlgorithm = publicKeyAlgorithm,
            sha256Fingerprint = sha256Fingerprint,
            filePath = filePath,
        )
    }

    companion object {
        fun from(value: EngineClientCertificate): ClientCertificateDocument = ClientCertificateDocument(
            alias = value.alias,
            subject = value.subject,
            host = value.host,
            expiration = value.expiration,
            enabled = value.enabled,
            format = value.format,
            daysUntilExpiration = value.daysUntilExpiration,
            subjectDn = value.subjectDn,
            issuerDn = value.issuerDn,
            serialNumber = value.serialNumber,
            sanList = value.sanList,
            publicKeyAlgorithm = value.publicKeyAlgorithm,
            sha256Fingerprint = value.sha256Fingerprint,
            filePath = value.filePath,
        )
    }
}

@Serializable
private data class MtlsRuleDocument(
    val ruleName: String,
    val hostPattern: String,
    val certificateAlias: String,
    val enabled: Boolean,
) {
    fun toEngine(): EngineMtlsRule = EngineMtlsRule(ruleName, hostPattern, certificateAlias, enabled)

    companion object {
        fun from(value: EngineMtlsRule): MtlsRuleDocument = MtlsRuleDocument(
            ruleName = value.ruleName,
            hostPattern = value.hostPattern,
            certificateAlias = value.certificateAlias,
            enabled = value.enabled,
        )
    }
}
