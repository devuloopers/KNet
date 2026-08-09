package com.devuloopers.knet.engine.certificate

/**
 * Public facade interface exposing the engine capabilities for CA certificate management,
 * trust keystore installations, client certificate registrations, and wildcard mTLS routing configuration.
 */
public interface CertificateManager {

    /**
     * Retrieves the execution status of the Certificate Authority.
     *
     * @return The status string representation (e.g. "AVAILABLE", "MISSING").
     */
    public fun getCaStatus(): String

    /**
     * Retrieves the X.500 Distinguished Name representing the Subject of the Root CA.
     *
     * @return The subject details string.
     */
    public fun getCaSubject(): String

    /**
     * Retrieves the X.500 Distinguished Name representing the Issuer of the Root CA.
     *
     * @return The issuer details string.
     */
    public fun getCaIssuer(): String

    /**
     * Retrieves the unique Serial Number of the Root CA.
     *
     * @return Colon-separated hexadecimal serial number string.
     */
    public fun getCaSerialNumber(): String

    /**
     * Retrieves the cryptographic signature algorithm identifier of the Root CA.
     *
     * @return The algorithm name (e.g. "SHA256withRSA").
     */
    public fun getCaSignatureAlgorithm(): String

    /**
     * Retrieves the ISO date string representing when the Root CA becomes valid.
     *
     * @return Validity starting date string.
     */
    public fun getCaValidFrom(): String

    /**
     * Retrieves the ISO date string representing when the Root CA ceases to be valid.
     *
     * @return Validity ending date string.
     */
    public fun getCaValidUntil(): String

    /**
     * Retrieves the SHA-1 digest fingerprint identifier of the Root CA.
     *
     * @return Hexadecimal SHA-1 digest fingerprint.
     */
    public fun getCaSha1Fingerprint(): String

    /**
     * Retrieves the SHA-256 digest fingerprint identifier of the Root CA.
     *
     * @return Hexadecimal SHA-256 digest fingerprint.
     */
    public fun getCaSha256Fingerprint(): String

    /**
     * Registers and installs the Root CA certificate into the local operating system trust store.
     *
     * @return True if trust setup completes successfully, false otherwise.
     */
    public fun installRootCertificate(): Boolean

    /**
     * Lists all imported client certificates registered for mutual TLS.
     *
     * @return List of active [EngineClientCertificate] entities.
     */
    public fun getClientCertificates(): List<EngineClientCertificate>

    /**
     * Imports a new client PKCS12 keypair or certificate.
     *
     * @param path Absolute filesystem path of the certificate file.
     * @param alias Unique alias descriptor used to identify this client identity.
     */
    public fun importClientCertificate(path: String, alias: String)

    /**
     * Exports a client keypair file to a path on disk.
     *
     * @param alias The unique alias of the client keypair to export.
     * @param destinationPath Target directory path.
     */
    public fun exportClientCertificate(alias: String, destinationPath: String)

    /**
     * Removes an imported client certificate keypair mapping.
     *
     * @param alias Unique alias descriptor of the certificate to delete.
     */
    public fun deleteClientCertificate(alias: String)

    /**
     * Toggles the enabled status of an imported client certificate identity.
     *
     * @param alias Unique alias descriptor of the certificate.
     * @param enabled The new active toggle state.
     */
    public fun toggleCertificateEnabled(alias: String, enabled: Boolean)

    /**
     * Lists all registered hostname matching rules.
     *
     * @return List of active [EngineMtlsRule] configurations.
     */
    public fun getMtlsRules(): List<EngineMtlsRule>

    /**
     * Appends a new hostname matching rule routing mapping.
     *
     * @param rule The [EngineMtlsRule] to register.
     */
    public fun addMtlsRule(rule: EngineMtlsRule)

    /**
     * Modifies fields of an existing rule mapping configuration.
     *
     * @param rule The modified [EngineMtlsRule] entity.
     */
    public fun editMtlsRule(rule: EngineMtlsRule)

    /**
     * Removes a rule configuration by name ID.
     *
     * @param ruleName Name identity identifier of the rule to delete.
     */
    public fun deleteMtlsRule(ruleName: String)
}
