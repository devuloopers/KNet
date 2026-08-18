package com.devuloopers.knet.engine.certificate

import kotlinx.serialization.Serializable

/**
 * Domain-level representation of an imported client certificate used for mutual TLS authentication.
 *
 * @property alias Friendly name descriptor used to reference the client key store.
 * @property subject X.500 Distinguished Name representing the certificate owner.
 * @property host Host matching wildcard pattern rule.
 * @property expiration Date string representing when the keypair is no longer valid.
 * @property enabled Toggle indicating whether this client identity is active.
 * @property format Encoding format identifier (e.g. PKCS12 or PEM).
 * @property daysUntilExpiration Calculated days remaining until certificate expiration.
 * @property subjectDn Full Subject Distinguished Name string.
 * @property issuerDn Full Issuer Distinguished Name string.
 * @property serialNumber Hexadecimal serial number string.
 * @property sanList Subject Alternative Names list.
 * @property publicKeyAlgorithm Public key algorithm description.
 * @property sha256Fingerprint SHA-256 fingerprint hex string.
 * @property filePath Absolute file path of saved keypair on disk.
 */
@Serializable
data class EngineClientCertificate(
    val alias: String,
    val subject: String,
    val host: String,
    val expiration: String,
    val enabled: Boolean = true,
    val format: String = "PKCS12",
    val daysUntilExpiration: Int = 365,
    val subjectDn: String = "",
    val issuerDn: String = "",
    val serialNumber: String = "",
    val sanList: List<String> = emptyList(),
    val publicKeyAlgorithm: String = "RSA 2048-bit",
    val sha256Fingerprint: String = "",
    val filePath: String = ""
)
