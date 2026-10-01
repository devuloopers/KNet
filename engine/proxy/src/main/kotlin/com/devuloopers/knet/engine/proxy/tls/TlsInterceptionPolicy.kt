package com.devuloopers.knet.engine.proxy.tls

/**
 * Stable identifier explaining which protected-traffic rule selected a TLS action.
 *
 * @property value Non-blank rule identity propagated to capture evidence.
 */
@JvmInline
value class TlsInterceptionRuleId(val value: String) {
    init {
        require(value.isNotBlank()) { "TLS interception rule ID must not be blank." }
    }
}

/**
 * Verified source-application identity supplied by a trusted platform ingress adapter.
 *
 * @property value Platform package or signing identifier.
 */
@JvmInline
value class TlsSourceApplicationId(val value: String) {
    init {
        require(value.isNotBlank()) { "TLS source application ID must not be blank." }
    }
}

/** Closed set of transport actions supported by the desktop CONNECT pipeline. */
enum class TlsInterceptionMode {
    /** Terminate downstream TLS with the KNet authority and inspect supported protocols. */
    INSPECT,

    /** Preserve end-to-end TLS and relay encrypted bytes without payload inspection. */
    TUNNEL,

    /** Reject the connection before certificate generation or upstream dialing. */
    BLOCK,
}

/** Original policy action retained even when desktop CONNECT maps BYPASS to a safe tunnel. */
enum class TlsInterceptionPolicyAction {
    INSPECT,
    TUNNEL,
    BLOCK,
    BYPASS,
}

/**
 * Bounded connection identity supplied to the protected-traffic decision boundary.
 *
 * @property connectHost Validated CONNECT authority host used for socket routing.
 * @property connectPort Validated CONNECT authority port.
 * @property serverName ClientHello server name when bounded classification has exposed one.
 * @property sourceApplication Verified source application when supplied by the ingress adapter.
 */
data class TlsInterceptionRequest(
    val connectHost: String,
    val connectPort: Int,
    val serverName: String? = null,
    val sourceApplication: TlsSourceApplicationId? = null,
) {
    init {
        require(connectHost.isNotBlank()) { "CONNECT host must not be blank." }
        require(connectPort in 1..65_535) { "CONNECT port must be between 1 and 65535." }
        require(serverName == null || serverName.isNotBlank()) { "TLS server name must not be blank." }
    }
}

/**
 * Explainable protected-traffic decision selected for one connection.
 *
 * @property mode Transport action supported by the CONNECT pipeline.
 * @property ruleId Optional stable rule evidence.
 * @property policyAction Original selected policy action.
 * @property policyGroupId Optional built-in compatibility-group evidence.
 */
data class TlsInterceptionDecision(
    val mode: TlsInterceptionMode,
    val ruleId: TlsInterceptionRuleId? = null,
    val policyAction: TlsInterceptionPolicyAction = mode.toPolicyAction(),
    val policyGroupId: String? = null,
) {
    init {
        require(policyGroupId == null || policyGroupId.isNotBlank()) {
            "TLS interception policy group ID must not be blank."
        }
    }
}

private fun TlsInterceptionMode.toPolicyAction(): TlsInterceptionPolicyAction = when (this) {
    TlsInterceptionMode.INSPECT -> TlsInterceptionPolicyAction.INSPECT
    TlsInterceptionMode.TUNNEL -> TlsInterceptionPolicyAction.TUNNEL
    TlsInterceptionMode.BLOCK -> TlsInterceptionPolicyAction.BLOCK
}

/** Pure decision port consulted before the proxy installs a downstream TLS certificate. */
fun interface TlsInterceptionPolicy {
    /** Returns the action and optional rule evidence for [request]. */
    fun decide(request: TlsInterceptionRequest): TlsInterceptionDecision

    companion object {
        /** Safe compatibility default retaining KNet's existing inspect-all behavior. */
        val InspectAll: TlsInterceptionPolicy = TlsInterceptionPolicy {
            TlsInterceptionDecision(TlsInterceptionMode.INSPECT)
        }
    }
}
