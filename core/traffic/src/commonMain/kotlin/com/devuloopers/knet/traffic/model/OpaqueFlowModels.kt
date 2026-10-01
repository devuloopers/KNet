package com.devuloopers.knet.traffic.model

import com.devuloopers.knet.traffic.id.ConnectionId
import com.devuloopers.knet.traffic.id.OpaqueFlowId

/** Transport protocol of a payload-opaque flow. */
public enum class OpaqueTransportProtocol {
    /** Ordered TCP byte stream. */
    TCP,

    /** UDP datagram flow. */
    UDP,
}

/** Security protocol proven for a payload-opaque flow. */
public enum class OpaqueSecurityProtocol {
    /** End-to-end TLS carried without certificate substitution. */
    TLS,

    /** QUIC identified from bounded packet evidence without HTTP/3 semantics. */
    QUIC,

    /** No security protocol was proven. */
    UNKNOWN,
}

/** Lifecycle state for a canonical payload-opaque transport flow. */
public enum class OpaqueFlowState {
    /** Flow has started and may receive counter updates. */
    ACTIVE,

    /** Flow ended normally. */
    COMPLETED,

    /** Flow ended because of a transport or runtime failure. */
    FAILED,

    /** Flow was rejected by an explicit policy decision. */
    DROPPED,

    /** Flow ownership ended before normal completion. */
    CANCELLED,
}

/** Protected-traffic action that produced an opaque record. */
public enum class OpaqueFlowPolicyAction {
    INSPECT,
    TUNNEL,
    BLOCK,
    BYPASS,
}

/**
 * Canonical payload-opaque flow metadata suitable for Traffic presentation and export.
 *
 * @property id Stable flow identity.
 * @property connectionId Owning admitted transport connection.
 * @property destination Validated remote endpoint.
 * @property serverName Visible TLS server name, when available.
 * @property transport Transport protocol.
 * @property security Proven security classification.
 * @property sourceApplicationId Verified platform source identity, when available.
 * @property policyRuleId Stable selected policy rule, or `null` for the global default.
 * @property appliedNetworkCondition Network-condition profile/rule evidence selected for this flow.
 * @property startedAtEpochMillis Flow start time on the Unix epoch.
 * @property completedAtEpochMillis Terminal time, or `null` while active.
 * @property uploadedBytes Monotonic bytes accepted from the client side.
 * @property downloadedBytes Monotonic bytes accepted from the destination side.
 * @property state Current canonical lifecycle.
 * @property terminalOutcome Typed terminal outcome, or `null` while active.
 * @property offeredApplicationProtocols Bounded ALPN tokens offered in the visible ClientHello.
 * @property offeredTlsVersions Bounded TLS versions offered in the visible ClientHello.
 */
public data class OpaqueFlowSnapshot(
    public val id: OpaqueFlowId,
    public val connectionId: ConnectionId,
    public val destination: TrafficEndpoint,
    public val serverName: String?,
    public val transport: OpaqueTransportProtocol,
    public val security: OpaqueSecurityProtocol,
    public val sourceApplicationId: String?,
    public val policyRuleId: String?,
    public val policyAction: OpaqueFlowPolicyAction? = null,
    public val policyGroupId: String? = null,
    public val appliedNetworkCondition: AppliedNetworkCondition? = null,
    public val startedAtEpochMillis: Long,
    public val completedAtEpochMillis: Long?,
    public val uploadedBytes: Long,
    public val downloadedBytes: Long,
    public val state: OpaqueFlowState,
    public val terminalOutcome: ExchangeTerminalOutcome?,
    public val offeredApplicationProtocols: List<String> = emptyList(),
    public val offeredTlsVersions: List<String> = emptyList(),
) {
    init {
        require(serverName == null || serverName.isNotBlank()) { "Opaque-flow server name must not be blank." }
        require(sourceApplicationId == null || sourceApplicationId.isNotBlank()) {
            "Opaque-flow source application ID must not be blank."
        }
        require(policyRuleId == null || policyRuleId.isNotBlank()) { "Opaque-flow rule ID must not be blank." }
        require(policyGroupId == null || policyGroupId.isNotBlank()) { "Opaque-flow policy group ID must not be blank." }
        require(startedAtEpochMillis >= 0L) { "Opaque-flow start time must not be negative." }
        require(completedAtEpochMillis == null || completedAtEpochMillis >= startedAtEpochMillis) {
            "Opaque-flow completion cannot precede its start."
        }
        require(uploadedBytes >= 0L && downloadedBytes >= 0L) { "Opaque-flow counters must not be negative." }
        require((state == OpaqueFlowState.ACTIVE) == (terminalOutcome == null)) {
            "Only active opaque flows may omit a terminal outcome."
        }
        require(offeredApplicationProtocols.size <= 16 && offeredTlsVersions.size <= 16)
        require((offeredApplicationProtocols + offeredTlsVersions).all { it.isNotBlank() && it.length <= 255 })
    }
}
