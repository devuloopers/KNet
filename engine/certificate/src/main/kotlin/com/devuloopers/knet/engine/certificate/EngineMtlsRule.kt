package com.devuloopers.knet.engine.certificate

/**
 * Domain-level representation of a mutual TLS host matching routing rule.
 *
 * @property ruleName Unique identifier for the routing rule.
 * @property hostPattern Wildcard hostname matching criteria.
 * @property certificateAlias Friendly name of the imported client keypair mapped to this host.
 * @property enabled Toggle indicating whether this rule is active.
 */
data class EngineMtlsRule(
    val ruleName: String,
    val hostPattern: String,
    val certificateAlias: String,
    val enabled: Boolean = true
)
