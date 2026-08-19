package com.devuloopers.knet.domain.rules.model

/**
 * Stable identifier owned by one breakpoint protocol extension.
 *
 * The value is deliberately open so adding a protocol does not require modifying a central enum or
 * sealed hierarchy. Standard identifiers are exposed as typed constants.
 */
@JvmInline
value class BreakpointProtocolId(val value: String) {
    init {
        require(value.isNotBlank()) { "Breakpoint protocol ID must not be blank." }
        require(value == value.trim().lowercase()) {
            "Breakpoint protocol ID must be a normalized lowercase token."
        }
        require(value.first().isLetter() && value.all { it.isLetterOrDigit() || it == '-' || it == '.' }) {
            "Breakpoint protocol ID contains unsupported characters."
        }
    }

    companion object {
        /** Protocol-neutral HTTP rule matching. */
        val HTTP = BreakpointProtocolId("http")
    }
}

/**
 * Persistable reference to extension-owned breakpoint criteria.
 *
 * [encodedPayload] is opaque outside the extension identified by [protocolId]. The owning
 * extension validates and compiles it into a strongly typed matcher. This envelope lets storage,
 * application coordination, and UI selection remain stable as protocols are added.
 */
data class ProtocolMatchCriteria(
    val protocolId: BreakpointProtocolId = BreakpointProtocolId.HTTP,
    val encodedPayload: String = "",
) {
    companion object {
        /** Default criteria that applies only phase, HTTP method, and URL matching. */
        val HttpDefault = ProtocolMatchCriteria()
    }
}
