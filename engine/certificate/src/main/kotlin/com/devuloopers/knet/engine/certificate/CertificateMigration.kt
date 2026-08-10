package com.devuloopers.knet.engine.certificate

/**
 * Migration parser responsible solely for converting legacy pipe-delimited text records into domain models.
 * Performs no file I/O, JSON serialization, or logging operations.
 */
internal object CertificateMigration {

    /**
     * Parses legacy pipe-delimited client certificate records.
     * Returns parsed list, or null if the content does not contain valid pipe-delimited records.
     */
    fun parseLegacyClientCertificates(content: String): List<EngineClientCertificate>? {
        val validLines = content.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && it.contains("|") }

        if (validLines.isEmpty()) return null

        val items = validLines.mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size >= 4) {
                EngineClientCertificate(
                    alias = parts[0],
                    subject = parts[1],
                    host = parts[2],
                    expiration = parts[3],
                    enabled = parts.getOrNull(4)?.toBooleanStrictOrNull() ?: true,
                    format = parts.getOrNull(5) ?: "PKCS12",
                    daysUntilExpiration = parts.getOrNull(6)?.toIntOrNull() ?: 365,
                    subjectDn = parts.getOrNull(7) ?: parts[1],
                    issuerDn = parts.getOrNull(8) ?: "",
                    serialNumber = parts.getOrNull(9) ?: "",
                    sanList = parts.getOrNull(10)?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                    publicKeyAlgorithm = parts.getOrNull(11) ?: "RSA 2048-bit",
                    sha256Fingerprint = parts.getOrNull(12) ?: "",
                    filePath = parts.getOrNull(13) ?: ""
                )
            } else null
        }

        return if (items.isNotEmpty()) items else null
    }

    /**
     * Parses legacy pipe-delimited mTLS routing rules.
     * Returns parsed list, or null if the content does not contain valid pipe-delimited records.
     */
    fun parseLegacyMtlsRules(content: String): List<EngineMtlsRule>? {
        val validLines = content.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && it.contains("|") }

        if (validLines.isEmpty()) return null

        val items = validLines.mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size >= 3) {
                EngineMtlsRule(
                    ruleName = parts[0],
                    hostPattern = parts[1],
                    certificateAlias = parts[2],
                    enabled = parts.getOrNull(3)?.toBooleanStrictOrNull() ?: true
                )
            } else null
        }

        return if (items.isNotEmpty()) items else null
    }
}
