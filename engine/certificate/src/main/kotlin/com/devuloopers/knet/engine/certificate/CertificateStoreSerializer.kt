package com.devuloopers.knet.engine.certificate

import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.core.logger.LogTags
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Internal persistence serializer responsible for reading and writing client certificate
 * and mTLS domain rule records to disk as structured JSON files.
 */
internal object CertificateStoreSerializer {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Reads persisted [EngineClientCertificate] entries from [file].
     */
    fun loadClientCertificates(file: File?): List<EngineClientCertificate> {
        if (file == null || !file.exists()) return emptyList()
        return try {
            val content = file.readText().trim()
            if (content.isBlank()) emptyList() else json.decodeFromString(content)
        } catch (e: Exception) {
            KNetLogger.warn(LogTags.CERTIFICATE) {
                "[CertStore] Failed to parse client certificates JSON from '${file.absolutePath}': ${e.message}"
            }
            emptyList()
        }
    }

    /**
     * Reads persisted [EngineMtlsRule] entries from [file].
     */
    fun loadMtlsRules(file: File?): List<EngineMtlsRule> {
        if (file == null || !file.exists()) return emptyList()
        return try {
            val content = file.readText().trim()
            if (content.isBlank()) emptyList() else json.decodeFromString(content)
        } catch (e: Exception) {
            KNetLogger.warn(LogTags.CERTIFICATE) {
                "[CertStore] Failed to parse mTLS rules JSON from '${file.absolutePath}': ${e.message}"
            }
            emptyList()
        }
    }

    /**
     * Writes [certificates] list to [file] as structured JSON.
     */
    fun persistClientCertificates(file: File?, certificates: List<EngineClientCertificate>) {
        if (file == null) return
        try {
            val content = json.encodeToString(certificates)
            file.writeText(content)
        } catch (e: Exception) {
            KNetLogger.error(LogTags.CERTIFICATE, e) {
                "[CertStore] Failed to serialize client certificates to disk"
            }
        }
    }

    /**
     * Writes [rules] list to [file] as structured JSON.
     */
    fun persistMtlsRules(file: File?, rules: List<EngineMtlsRule>) {
        if (file == null) return
        try {
            val content = json.encodeToString(rules)
            file.writeText(content)
        } catch (e: Exception) {
            KNetLogger.error(LogTags.CERTIFICATE, e) {
                "[CertStore] Failed to serialize mTLS rules to disk"
            }
        }
    }
}
