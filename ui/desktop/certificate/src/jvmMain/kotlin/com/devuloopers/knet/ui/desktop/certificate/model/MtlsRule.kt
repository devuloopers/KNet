package com.devuloopers.knet.ui.desktop.certificate.model

/**
 * Presentation model describing mTLS matching wildcard hostname mapping rules.
 */
public data class MtlsRule(
    val ruleName: String,
    val hostPattern: String,
    val certificateAlias: String,
    val enabled: Boolean = true
)
