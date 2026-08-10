package com.devuloopers.knet.engine.certificate

import kotlinx.serialization.json.Json

/**
 * Pure JSON serializer for client certificate and mTLS domain rule models.
 * Uses kotlinx.serialization and performs no file operations or logging.
 */
internal object CertificateSerializer {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Serializes a list of [EngineClientCertificate] objects to JSON text.
     */
    fun encodeClientCertificates(certificates: List<EngineClientCertificate>): String =
        json.encodeToString(certificates)

    /**
     * Deserializes a JSON string into a list of [EngineClientCertificate] objects.
     */
    fun decodeClientCertificates(content: String): List<EngineClientCertificate> =
        json.decodeFromString(content)

    /**
     * Serializes a list of [EngineMtlsRule] objects to JSON text.
     */
    fun encodeMtlsRules(rules: List<EngineMtlsRule>): String =
        json.encodeToString(rules)

    /**
     * Deserializes a JSON string into a list of [EngineMtlsRule] objects.
     */
    fun decodeMtlsRules(content: String): List<EngineMtlsRule> =
        json.decodeFromString(content)
}
