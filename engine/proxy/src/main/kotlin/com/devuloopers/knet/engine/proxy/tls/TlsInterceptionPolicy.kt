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

/** Trusted client and capture generation used only to isolate temporary TLS fallback. */
data class TlsInterceptionClientContext(
    /** Current canonical capture generation. */
    val sessionId: com.devuloopers.knet.traffic.id.CaptureSessionId,
    /** Client scope derived before protocol parsing. */
    val client: com.devuloopers.knet.traffic.model.TlsFallbackClientScope,
)

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
    /** Available only when admitted transport metadata and active capture establish client isolation. */
    val clientContext: TlsInterceptionClientContext? = null,
    /** Automatic fallback is selected only after a bounded ClientHello classification, including absent SNI. */
    val clientHelloObserved: Boolean = false,

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
    /** Epoch authorizing a later proven rejection; absent for manual/unsupported inspection. */
    val automaticFallbackGeneration: Long? = null,
    /** Exact decision source retained in opaque capture. */
    val origin: com.devuloopers.knet.traffic.model.ProtectionDecisionOrigin = com.devuloopers.knet.traffic.model.ProtectionDecisionOrigin.GLOBAL,

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

    /** Non-blocking downstream outcome callback; ordinary inspect-only policies do not learn. */
    fun inspectionFailed(request: TlsInterceptionRequest, decision: TlsInterceptionDecision, failure: TlsInspectionFailure) = Unit

    companion object {
        /** Safe compatibility default retaining KNet's existing inspect-all behavior. */
        val InspectAll: TlsInterceptionPolicy = TlsInterceptionPolicy {
            TlsInterceptionDecision(TlsInterceptionMode.INSPECT)
        }
    }
}
