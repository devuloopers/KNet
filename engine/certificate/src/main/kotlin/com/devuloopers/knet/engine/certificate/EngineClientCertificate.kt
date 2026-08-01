package com.devuloopers.knet.engine.certificate

/**
 * Domain-level representation of an imported client certificate used for mutual TLS authentication.
 *
 * @property alias Friendly name descriptor used to reference the client key store.
 * @property subject X.500 Distinguished Name representing the certificate owner.
 * @property host Host matching wildcard pattern rule.
 * @property expiration Date string representing when the keypair is no longer valid.
 * @property enabled Toggle indicating whether this client identity is active.
 */
public data class EngineClientCertificate(
    val alias: String,
    val subject: String,
    val host: String,
    val expiration: String,
    val enabled: Boolean = true
)
