package com.devuloopers.knet.application.contract.protectedtraffic

import com.devuloopers.knet.domain.protectedtraffic.*
import com.devuloopers.knet.traffic.id.CaptureSessionId
import com.devuloopers.knet.traffic.model.TlsFallbackClientScope
import kotlinx.coroutines.flow.StateFlow

/** Exact temporary selector; no wildcard, payload, credentials or URL paths are retained. */
public data class AutomaticTlsFallbackKey(
    /** Owning capture generation; old generations can never apply to a new session. */
    public val sessionId: CaptureSessionId,
    /** Proxy-attributed client boundary. */
    public val client: TlsFallbackClientScope,
    /** Normalized exact hostname or IP address. */
    public val destination: ProtectedDestinationSelector.Exact,
    /** Exact TLS destination port. */
    public val port: Int,
    /** OS-verified source identity when available; unknown identity is a separate scope. */
    public val sourceApplication: ProtectedSourceApplicationId? = null,
) { init { require(port in 1..65535) } }

/** Immutable bounded cache view for current-handling presentation, independent of captured history. */
public data class AutomaticTlsFallbackState(
    /** Current active capture generation, absent while paused/stopped. */
    public val sessionId: CaptureSessionId? = null,
    /** Mutation epoch fencing late handshakes after policy, session, certificate or manual-choice changes. */
    public val generation: Long = 0,
    /** Currently eligible, unexpired exact client/destination selectors. */
    public val keys: Set<AutomaticTlsFallbackKey> = emptySet(),
)

/** Non-blocking temporary decision boundary shared by TLS composition and application workflows. */
public interface AutomaticTlsFallback {
    /** Current active session and eligible decisions; expiry publishes removals. */
    public val state: StateFlow<AutomaticTlsFallbackState>
    /** Replaces session ownership and forgets every prior decision and in-flight learning token. */
    public fun activateSession(sessionId: CaptureSessionId?)
    /** Rebinds unexpired decisions across a capture-only writer rotation; forwarding identity is unchanged. */
    public fun rotateCaptureSession(sessionId: CaptureSessionId) { activateSession(sessionId) }

    /** Synchronizes policy/authority; returns an epoch only when automatic inspection is allowed. */
    public fun authorize(configuration: ProtectedTrafficConfiguration, authorityIdentity: String?): Long?
    /** Records a proven rejection only if the original authorized epoch/session remains current. */
    public fun record(key: AutomaticTlsFallbackKey, generation: Long): Boolean
    /** Clears decisions and invalidates pending handshakes after an explicit user choice. */
    public fun clear()
    /** Forgets matching temporary destinations and fences pending outcomes while retaining unrelated decisions. */
    public fun forget(rule: ProtectedTrafficRule)
}

/** Constructs an exact normalized key; unavailable client/session/destination evidence disables learning. */
public fun automaticTlsFallbackKey(sessionId: CaptureSessionId?, client: TlsFallbackClientScope?, host: String,
    port: Int?, sourceApplication: String?): AutomaticTlsFallbackKey? {
    if (sessionId == null || client == null || port == null || port !in 1..65535) return null
    val destination = runCatching { ProtectedDestinationSelector.parse(host) }.getOrNull() as? ProtectedDestinationSelector.Exact ?: return null
    return AutomaticTlsFallbackKey(sessionId, client, destination, port, sourceApplication?.let(::ProtectedSourceApplicationId))
}
