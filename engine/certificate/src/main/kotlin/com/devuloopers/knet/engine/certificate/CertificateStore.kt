package com.devuloopers.knet.engine.certificate

import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.core.logger.LogTags
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Certificate store coordinator owning filesystem persistence, atomic file updates,
 * and load lifecycle coordination for client certificates and mTLS rules.
 *
 * @param file Target JSON persistence file on disk.
 */
internal class CertificateStore(
    private val file: File?
) {
    private val writeLock = Any()

    /**
     * Reads persisted [EngineClientCertificate] entries.
     */
    fun loadClientCertificates(): List<EngineClientCertificate> {
        if (file == null || !file.exists()) return emptyList()
        val content = readContentSafe() ?: return emptyList()
        if (content.isBlank()) return emptyList()

        try {
            return CertificateSerializer.decodeClientCertificates(content)
        } catch (_: Exception) {
            KNetLogger.warn(LogTags.CERTIFICATE) {
                "[CertStore] Failed to parse client certificates from '${file.absolutePath}'. File remains untouched."
            }
            return emptyList()
        }
    }

    /**
     * Writes [certificates] to disk using atomic temporary file replacement.
     */
    fun persistClientCertificates(certificates: List<EngineClientCertificate>) {
        val content = try {
            CertificateSerializer.encodeClientCertificates(certificates)
        } catch (e: Exception) {
            KNetLogger.error(LogTags.CERTIFICATE, e) {
                "[CertStore] Failed to serialize client certificates to JSON"
            }
            return
        }
        writeAtomically(content)
    }

    /**
     * Reads persisted [EngineMtlsRule] entries.
     */
    fun loadMtlsRules(): List<EngineMtlsRule> {
        if (file == null || !file.exists()) return emptyList()
        val content = readContentSafe() ?: return emptyList()
        if (content.isBlank()) return emptyList()

        try {
            return CertificateSerializer.decodeMtlsRules(content)
        } catch (_: Exception) {
            KNetLogger.warn(LogTags.CERTIFICATE) {
                "[CertStore] Failed to parse mTLS rules from '${file.absolutePath}'. File remains untouched."
            }
            return emptyList()
        }
    }

    /**
     * Writes [rules] to disk using atomic temporary file replacement.
     */
    fun persistMtlsRules(rules: List<EngineMtlsRule>) {
        val content = try {
            CertificateSerializer.encodeMtlsRules(rules)
        } catch (e: Exception) {
            KNetLogger.error(LogTags.CERTIFICATE, e) {
                "[CertStore] Failed to serialize mTLS rules to JSON"
            }
            return
        }
        writeAtomically(content)
    }

    private fun readContentSafe(): String? {
        return try {
            file?.readText()?.trim()
        } catch (e: Exception) {
            KNetLogger.error(LogTags.CERTIFICATE, e) {
                "[CertStore] Failed to read file '${file?.absolutePath}'"
            }
            null
        }
    }

    private fun writeAtomically(content: String) {
        if (file == null) return
        synchronized(writeLock) {
            try {
                file.parentFile?.let(CertificateFileSecurity::secureDirectory)
                val tempFile = File.createTempFile("cert_store_", ".tmp", file.parentFile)
                try {
                    tempFile.writeText(content)
                    CertificateFileSecurity.secureSecretFile(tempFile)
                    try {
                        Files.move(
                            tempFile.toPath(),
                            file.toPath(),
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE
                        )
                    } catch (_: Exception) {
                        // Fallback if ATOMIC_MOVE is not supported by filesystem
                        Files.move(
                            tempFile.toPath(),
                            file.toPath(),
                            StandardCopyOption.REPLACE_EXISTING
                        )
                    }
                    CertificateFileSecurity.secureSecretFile(file)
                } finally {
                    if (tempFile.exists()) {
                        tempFile.delete()
                    }
                }
            } catch (e: Exception) {
                KNetLogger.error(LogTags.CERTIFICATE, e) {
                    "[CertStore] Failed atomic write to file '${file.absolutePath}'"
                }
            }
        }
    }
}
