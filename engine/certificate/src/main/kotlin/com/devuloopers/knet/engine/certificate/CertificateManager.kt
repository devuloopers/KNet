package com.devuloopers.knet.engine.certificate

/**
 * Public facade interface exposing the engine capabilities for CA certificate management,
 * client certificate registrations and wildcard mTLS routing configuration.
 */
interface CertificateManager {
    /** Returns one coherent Root CA snapshot instead of a series of racy field reads. */
    fun getAuthorityDetails(): CertificateAuthorityDetails

    /**
     * Lists all imported client certificates registered for mutual TLS.
     *
     * @return List of active [EngineClientCertificate] entities.
     */
    fun getClientCertificates(): List<EngineClientCertificate>

    /**
     * Imports a new client PKCS12 keypair or certificate with an optional decryption passphrase.
     *
     * @param path Absolute filesystem path of the certificate file.
     * @param alias Unique alias descriptor used to identify this client identity.
     * @param passphrase Optional password for encrypted PKCS#12 keystore files.
     */
    fun importClientCertificate(path: String, alias: String, passphrase: String = "")

    /**
     * Exports a client keypair file to a path on disk.
     *
     * @param alias The unique alias of the client keypair to export.
     * @param destinationPath Target directory path.
     */
    fun exportClientCertificate(alias: String, destinationPath: String)

    /**
     * Removes an imported client certificate keypair mapping.
     *
     * @param alias Unique alias descriptor of the certificate to delete.
     */
    fun deleteClientCertificate(alias: String)

    /**
     * Toggles the enabled status of an imported client certificate identity.
     *
     * @param alias Unique alias descriptor of the certificate.
     * @param enabled The new active toggle state.
     */
    fun toggleCertificateEnabled(alias: String, enabled: Boolean)

    /**
     * Lists all registered hostname matching rules.
     *
     * @return List of active [EngineMtlsRule] configurations.
     */
    fun getMtlsRules(): List<EngineMtlsRule>

    /**
     * Appends a new hostname matching rule routing mapping.
     *
     * @param rule The [EngineMtlsRule] to register.
     */
    fun addMtlsRule(rule: EngineMtlsRule)

    /**
     * Modifies fields of an existing rule mapping configuration.
     *
     * @param rule The modified [EngineMtlsRule] entity.
     */
    fun editMtlsRule(rule: EngineMtlsRule)

    /**
     * Removes a rule configuration by name ID.
     *
     * @param ruleName Name identity identifier of the rule to delete.
     */
    fun deleteMtlsRule(ruleName: String)

    /**
     * Resolves an initialized [javax.net.ssl.KeyManagerFactory] for outbound mTLS requests matching the target host.
     *
     * @param host The target hostname to evaluate against active mTLS domain rules.
     * @return Initialized [javax.net.ssl.KeyManagerFactory] if a matching active rule is found, or null otherwise.
     */
    fun getKeyManagerFactory(host: String): javax.net.ssl.KeyManagerFactory?
}
