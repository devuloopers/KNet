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
     * Tries JSON decoding first; falls back to legacy pipe migration if needed.
     */
    fun loadClientCertificates(): List<EngineClientCertificate> {
        if (file == null || !file.exists()) return emptyList()
        val content = readContentSafe() ?: return emptyList()
        if (content.isBlank()) return emptyList()

        // 1. Try standard JSON decoding
        try {
            return CertificateSerializer.decodeClientCertificates(content)
        } catch (_: Exception) {
            // Intentionally fall through to legacy migration
        }

        // 2. Try legacy migration
        val migrated = CertificateMigration.parseLegacyClientCertificates(content)
        if (migrated != null && migrated.isNotEmpty()) {
            KNetLogger.info(LogTags.CERTIFICATE) {
                "[CertStore] Migrated ${migrated.size} legacy client certificates to JSON"
            }
            persistClientCertificates(migrated)
            return migrated
        }

        // 3. Both failed - log warning and return empty list WITHOUT overwriting the original file
        KNetLogger.warn(LogTags.CERTIFICATE) {
            "[CertStore] Failed to parse client certificates from '${file.absolutePath}'. File remains untouched."
        }
        return emptyList()
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
     * Tries JSON decoding first; falls back to legacy pipe migration if needed.
     */
    fun loadMtlsRules(): List<EngineMtlsRule> {
        if (file == null || !file.exists()) return emptyList()
        val content = readContentSafe() ?: return emptyList()
        if (content.isBlank()) return emptyList()

        // 1. Try standard JSON decoding
        try {
            return CertificateSerializer.decodeMtlsRules(content)
        } catch (_: Exception) {
            // Intentionally fall through to legacy migration
        }

        // 2. Try legacy migration
        val migrated = CertificateMigration.parseLegacyMtlsRules(content)
        if (migrated != null && migrated.isNotEmpty()) {
            KNetLogger.info(LogTags.CERTIFICATE) {
                "[CertStore] Migrated ${migrated.size} legacy mTLS rules to JSON"
            }
            persistMtlsRules(migrated)
            return migrated
        }

        // 3. Both failed - log warning and return empty list WITHOUT overwriting the original file
        KNetLogger.warn(LogTags.CERTIFICATE) {
            "[CertStore] Failed to parse mTLS rules from '${file.absolutePath}'. File remains untouched."
        }
        return emptyList()
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
                file.parentFile?.mkdirs()
                val tempFile = File.createTempFile("cert_store_", ".tmp", file.parentFile)
                try {
                    tempFile.writeText(content)
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
