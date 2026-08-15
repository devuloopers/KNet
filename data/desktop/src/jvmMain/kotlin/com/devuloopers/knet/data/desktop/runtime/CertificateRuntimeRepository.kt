package com.devuloopers.knet.data.desktop.runtime

import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.certificate.InstallationResult
import com.devuloopers.knet.engine.certificate.TrustStoreInstaller
import com.devuloopers.knet.engine.certificate.ssl.KNetTrustManagerProvider
import java.io.File

/**
 * Desktop runtime coordinator managing Root CA certificates, truststore installation, and certificate caching.
 */
class CertificateRuntimeRepository(
    baseDir: File
) {
    private val caDir = File(baseDir, "ca").apply { mkdirs() }
    private val caCertFile = File(caDir, "ca.crt")
    private val caKeyFile = File(caDir, "ca.key")

    val certificateAuthority: CertificateAuthority = if (caCertFile.exists() && caKeyFile.exists()) {
        CertificateAuthority.loadFromPem(caCertFile, caKeyFile)
    } else {
        val generated = CertificateAuthority.generate()
        generated.saveToPem(caCertFile, caKeyFile)
        KNetTrustManagerProvider.invalidateCache()
        generated
    }

    val certificateCache: CertificateCache = CertificateCache()

    /**
     * Installs the KNet Root CA into the system truststore.
     */
    fun installRootCa(): Boolean {
        return try {
            val result = TrustStoreInstaller.install(certificateAuthority.certificate)
            if (result is InstallationResult.Success) {
                KNetTrustManagerProvider.invalidateCache()
            }
            result is InstallationResult.Success
        } catch (e: Exception) {
            KNetLogger.error(tag = "CertificateRuntimeRepository", throwable = e) {
                "Failed to install Root CA: ${e.message}"
            }
            false
        }
    }
}
