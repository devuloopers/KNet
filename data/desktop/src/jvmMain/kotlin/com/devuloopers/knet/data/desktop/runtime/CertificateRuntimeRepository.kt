package com.devuloopers.knet.data.desktop.runtime

import com.devuloopers.knet.data.desktop.certificate.DesktopCertificateConfigurationStore
import com.devuloopers.knet.data.desktop.certificate.DesktopRootTrustController
import com.devuloopers.knet.data.desktop.certificate.DesktopServerTlsContextProvider
import com.devuloopers.knet.data.desktop.certificate.InstallationResult
import com.devuloopers.knet.data.desktop.certificate.TrustStoreInstaller
import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.certificate.CertificateFileSecurity
import com.devuloopers.knet.engine.certificate.CertificateManager
import com.devuloopers.knet.engine.certificate.CertificateManagerImpl
import com.devuloopers.knet.engine.proxy.tls.ServerTlsContextProvider
import java.io.File

/**
 * Desktop runtime coordinator managing Root CA files, OS trust integration, and certificate caching.
 */
class CertificateRuntimeRepository(
    baseDir: File
) : DesktopRootTrustController {
    private val caDir = File(baseDir, "ca").also { directory ->
        check(CertificateFileSecurity.secureDirectory(directory)) {
            "Unable to secure certificate authority directory '${directory.absolutePath}'."
        }
    }
    private val caCertFile = File(caDir, "ca.crt")
    private val caKeyFile = File(caDir, "ca.key")

    private val certificateAuthority: CertificateAuthority = loadOrCreateCertificateAuthority()

    private val certificateCache: CertificateCache = CertificateCache()

    fun createCertificateManager(certificatesDirectory: File): CertificateManager =
        CertificateManagerImpl(
            ca = certificateAuthority,
            identityDirectory = certificatesDirectory,
            configurationStore = DesktopCertificateConfigurationStore(
                certificatesDirectory.resolve("certificate_configuration.json")
            ),
        )

    fun serverTlsContextProvider(): ServerTlsContextProvider =
        DesktopServerTlsContextProvider(certificateAuthority, certificateCache)

    fun rootCertificateDer(): ByteArray = certificateAuthority.certificate.encoded.copyOf()

    override fun installRootCertificate(): InstallationResult = TrustStoreInstaller.install(
        caCertificate = certificateAuthority.certificate,
        manualCertificateFile = caDir.resolve("knet-root-ca.crt"),
    )

    override fun isRootCertificateTrusted(): Boolean =
        TrustStoreInstaller.isTrusted(certificateAuthority.certificate)

    private fun loadOrCreateCertificateAuthority(): CertificateAuthority {
        if (caCertFile.isFile && caKeyFile.isFile) {
            check(CertificateFileSecurity.secureSecretFile(caCertFile))
            check(CertificateFileSecurity.secureSecretFile(caKeyFile))
            runCatching { CertificateAuthority.loadFromPem(caCertFile, caKeyFile) }
                .onFailure { error ->
                    KNetLogger.error(tag = "CertificateRuntimeRepository", throwable = error) {
                        "Stored Root CA is invalid; preserving the invalid material and generating a replacement."
                    }
                    preserveInvalidMaterial(caCertFile)
                    preserveInvalidMaterial(caKeyFile)
                }
                .getOrNull()
                ?.let { return it }
        } else if (caCertFile.exists() || caKeyFile.exists()) {
            preserveInvalidMaterial(caCertFile)
            preserveInvalidMaterial(caKeyFile)
        }

        return CertificateAuthority.generate().also { generated ->
            generated.saveToPem(caCertFile, caKeyFile)
        }
    }

    private fun preserveInvalidMaterial(file: File) {
        if (!file.exists()) return
        val backup = File(file.parentFile, "${file.name}.invalid-${kotlin.time.Clock.System.now().toEpochMilliseconds()}")
        if (!file.renameTo(backup)) {
            throw IllegalStateException("Unable to preserve invalid certificate material '${file.absolutePath}'.")
        }
        check(CertificateFileSecurity.secureSecretFile(backup))
    }
}
