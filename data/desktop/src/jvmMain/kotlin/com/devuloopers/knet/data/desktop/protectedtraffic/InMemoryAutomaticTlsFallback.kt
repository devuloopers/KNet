package com.devuloopers.knet.data.desktop.protectedtraffic

import com.devuloopers.knet.application.contract.protectedtraffic.*
import com.devuloopers.knet.domain.protectedtraffic.*
import com.devuloopers.knet.domain.policy.PolicyWorkspace
import com.devuloopers.knet.traffic.id.CaptureSessionId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Bounded process-local TLS decisions. No Room rows, per-flow jobs, payloads or remote probes are created.
 * All transport/UI accesses use one short lock; the product-owned scope runs one cancellable expiry timer.
 * @param scope Product lifetime; cancellation ends expiry work.
 * @param lifetimeMillis Fixed lifetime from first rejection; reads and repeated failures do not extend it.
 * @param maximumEntries Upper bound; oldest admitted decisions are evicted first.
 * @param elapsedMillis Monotonic clock, injectable for deterministic expiry tests.
 */
class InMemoryAutomaticTlsFallback(
    scope: CoroutineScope,
    private val lifetimeMillis: Long = 30 * 60 * 1_000L,
    private val maximumEntries: Int = 1_024,
    private val elapsedMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) : AutomaticTlsFallback {
    private val lock = Any()
    private val mutableState = MutableStateFlow(AutomaticTlsFallbackState())
    override val state = mutableState.asStateFlow()
    private val deadlines = linkedMapOf<AutomaticTlsFallbackKey, Long>()
    private data class Authorization(val configuration: ProtectedTrafficConfiguration, val generation: Long, val eligible: Boolean)
    private val authorizations = linkedMapOf<PolicyWorkspace, Authorization>()
    private var legacyAuthorization: Authorization? = null
    private var authority: String? = null
    private var nextGeneration = 0L

    init {
        require(lifetimeMillis in 1..86_400_000L && maximumEntries in 1..16_384)
        scope.launch {
            state.collectLatest {
                val remaining = synchronized(lock) { deadlines.values.minOrNull()?.let { deadline -> deadline - elapsedMillis() } }
                if (remaining != null) {
                    delay(remaining.coerceAtLeast(1))
                    synchronized(lock) { prune() }
                }
            }
        }
    }

    override fun activateSession(sessionId: CaptureSessionId?) = synchronized(lock) {
        if (state.value.sessionId != sessionId) {
            deadlines.clear()
            authorizations.clear()
            legacyAuthorization = null
            mutableState.value = AutomaticTlsFallbackState(sessionId, ++nextGeneration)
        }
    }

    override fun rotateCaptureSession(sessionId: CaptureSessionId) = synchronized(lock) {
        prune()
        val retained = deadlines.entries.map { (key, deadline) -> key.copy(sessionId = sessionId) to deadline }
        deadlines.clear()
        retained.forEach { (key, deadline) -> deadlines[key] = deadline }
        authorizations.clear()
        legacyAuthorization = null
        mutableState.value = state.value.copy(sessionId = sessionId, generation = ++nextGeneration, keys = deadlines.keys.toSet())
    }

    override fun authorize(configuration: ProtectedTrafficConfiguration, authorityIdentity: String?): Long? = synchronized(lock) {
        val previous = legacyAuthorization
        val result = authorizeWorkspace(PolicyWorkspace.Other, configuration, authorityIdentity)
        if (previous?.configuration != configuration) {
            val eligible = authorizations[PolicyWorkspace.Other]?.eligible == true
            deadlines.keys.removeAll { !eligible || ProtectedTrafficPolicy().evaluate(configuration, it.request()).evidence != ProtectedTrafficDecisionEvidence.GlobalDefault }
            publish()
        }
        // Legacy embedders have one policy, while production always uses explicit workspace authorization.
        legacyAuthorization = authorizations[PolicyWorkspace.Other]
        result
    }

    override fun authorizeWorkspace(workspace: PolicyWorkspace, configuration: ProtectedTrafficConfiguration, authorityIdentity: String?): Long? = synchronized(lock) {
        if (authority != authorityIdentity) {
            authority = authorityIdentity
            authorizations.clear()
            legacyAuthorization = null
            clearLocked()
        }
        val previous = authorizations[workspace]
        if (previous?.configuration != configuration) {
            val eligible = configuration.configurationReady && configuration.defaultAction == ProtectedTrafficAction.INSPECT &&
                configuration.automaticTlsFallbackEnabled && authorityIdentity != null
            deadlines.keys.removeAll { key ->
                PolicyWorkspace.fromClient(key.client) == workspace && (!eligible ||
                    ProtectedTrafficPolicy().evaluate(configuration, key.request()).evidence != ProtectedTrafficDecisionEvidence.GlobalDefault)
            }
            while (authorizations.size >= maximumEntries && workspace !in authorizations) authorizations.remove(authorizations.keys.first())
            val generation = ++nextGeneration
            authorizations[workspace] = Authorization(configuration, generation, eligible)
            mutableState.value = state.value.copy(generation = generation, keys = deadlines.keys.toSet())
        }
        prune()
        authorizations[workspace]?.takeIf { it.eligible && state.value.sessionId != null }?.generation
    }

    override fun record(key: AutomaticTlsFallbackKey, generation: Long): Boolean = synchronized(lock) {
        prune()
        val authorization = authorizations[PolicyWorkspace.fromClient(key.client)] ?: legacyAuthorization
        if (authorization?.eligible != true || state.value.sessionId != key.sessionId || authorization.generation != generation) return false
        if (key !in deadlines) {
            while (deadlines.size >= maximumEntries) deadlines.remove(deadlines.keys.first())
            deadlines[key] = elapsedMillis() + lifetimeMillis
            publish()
        }
        true
    }

    override fun clear() = synchronized(lock) { clearLocked() }

    override fun clearWorkspace(workspace: PolicyWorkspace) = synchronized(lock) {
        deadlines.keys.removeAll { PolicyWorkspace.fromClient(it.client) == workspace }
        authorizations.remove(workspace)
        if (workspace == PolicyWorkspace.Other) legacyAuthorization = null
        mutableState.value = state.value.copy(generation = ++nextGeneration, keys = deadlines.keys.toSet())
    }

    override fun forgetWorkspace(workspace: PolicyWorkspace, rule: ProtectedTrafficRule) = synchronized(lock) {
        forgetMatching(rule, workspace)
    }

    override fun forget(rule: ProtectedTrafficRule) = synchronized(lock) { forgetMatching(rule, null) }

    private fun forgetMatching(rule: ProtectedTrafficRule, workspace: PolicyWorkspace?) {
        val selection = ProtectedTrafficConfiguration(rules = listOf(rule.copy(enabled = true)), enabledBuiltInGroups = setOfNotNull(rule.groupId))
        deadlines.keys.removeAll { key -> (workspace == null || PolicyWorkspace.fromClient(key.client) == workspace) &&
            ProtectedTrafficPolicy().evaluate(selection, key.request()).evidence is ProtectedTrafficDecisionEvidence.Rule }
        if (workspace == null) { authorizations.clear(); legacyAuthorization = null }
        else { authorizations.remove(workspace); if (workspace == PolicyWorkspace.Other) legacyAuthorization = null }
        mutableState.value = state.value.copy(generation = ++nextGeneration, keys = deadlines.keys.toSet())
    }

    private fun AutomaticTlsFallbackKey.request() = ProtectedTrafficRequest(
        sourceApplication = sourceApplication, destinationHost = destination.value.takeUnless { destination.isIpLiteral },
        destinationAddress = destination.value.takeIf { destination.isIpLiteral }, port = port, transport = ProtectedTransportProtocol.TCP,
    )

    private fun clearLocked() {
        deadlines.clear()
        authorizations.clear()
        legacyAuthorization = null
        mutableState.value = state.value.copy(generation = ++nextGeneration, keys = emptySet())
    }

    private fun prune() {
        val now = elapsedMillis()
        if (deadlines.entries.removeAll { it.value <= now }) publish()
    }

    private fun publish() { mutableState.value = state.value.copy(keys = deadlines.keys.toSet()) }
}
