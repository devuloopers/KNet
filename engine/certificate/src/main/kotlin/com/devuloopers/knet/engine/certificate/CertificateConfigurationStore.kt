package com.devuloopers.knet.engine.certificate

/** One coherent persisted snapshot of imported client identities and their host-selection rules. */
data class CertificateConfiguration(
    val clientCertificates: List<EngineClientCertificate> = emptyList(),
    val mtlsRules: List<EngineMtlsRule> = emptyList(),
)

/**
 * Persistence port consumed by the certificate engine.
 *
 * Implementations must replace the complete snapshot atomically. The engine deliberately knows
 * nothing about JSON, Room, files, or another persistence technology.
 */
interface CertificateConfigurationStore {
    fun load(): CertificateConfiguration

    fun persist(configuration: CertificateConfiguration)
}

/** Default store for isolated engine use and tests that do not request persistence. */
internal object VolatileCertificateConfigurationStore : CertificateConfigurationStore {
    override fun load(): CertificateConfiguration = CertificateConfiguration()

    override fun persist(configuration: CertificateConfiguration) = Unit
}
