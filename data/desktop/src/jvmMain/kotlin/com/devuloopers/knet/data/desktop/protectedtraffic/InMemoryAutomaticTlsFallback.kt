package com.devuloopers.knet.data.desktop.protectedtraffic

import com.devuloopers.knet.application.contract.protectedtraffic.*
import com.devuloopers.knet.domain.protectedtraffic.*
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
    private var configuration: ProtectedTrafficConfiguration? = null
    private var authority: String? = null
    private var eligible = false

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
            mutableState.value = AutomaticTlsFallbackState(sessionId, state.value.generation + 1)
        }
    }

    override fun rotateCaptureSession(sessionId: CaptureSessionId) = synchronized(lock) {
        prune()
        val retained = deadlines.entries.map { (key, deadline) -> key.copy(sessionId = sessionId) to deadline }
        deadlines.clear()
        retained.forEach { (key, deadline) -> deadlines[key] = deadline }
        mutableState.value = state.value.copy(sessionId = sessionId, generation = state.value.generation + 1, keys = deadlines.keys.toSet())
    }

    override fun authorize(configuration: ProtectedTrafficConfiguration, authorityIdentity: String?): Long? = synchronized(lock) {
        if (this.configuration != configuration || authority != authorityIdentity) {
            val retainUnrelated = this.configuration?.let { previous -> previous.configurationReady &&
                previous.defaultAction == ProtectedTrafficAction.INSPECT && previous.automaticTlsFallbackEnabled } == true &&
                configuration.configurationReady && configuration.defaultAction == ProtectedTrafficAction.INSPECT &&
                configuration.automaticTlsFallbackEnabled && authority == authorityIdentity && authorityIdentity != null
            this.configuration = configuration
            authority = authorityIdentity
            if (retainUnrelated) {
                deadlines.keys.removeAll { key -> ProtectedTrafficPolicy().evaluate(configuration, key.request()).evidence != ProtectedTrafficDecisionEvidence.GlobalDefault }
                mutableState.value = state.value.copy(generation = state.value.generation + 1, keys = deadlines.keys.toSet())
            } else clearLocked()
        }
        eligible = configuration.configurationReady && configuration.defaultAction == ProtectedTrafficAction.INSPECT &&
            configuration.automaticTlsFallbackEnabled && authorityIdentity != null
        prune()
        state.value.generation.takeIf { eligible && state.value.sessionId != null }
    }

    override fun record(key: AutomaticTlsFallbackKey, generation: Long): Boolean = synchronized(lock) {
        prune()
        if (!eligible || state.value.sessionId != key.sessionId || state.value.generation != generation) return false
        if (key !in deadlines) {
            while (deadlines.size >= maximumEntries) deadlines.remove(deadlines.keys.first())
            deadlines[key] = elapsedMillis() + lifetimeMillis
            publish()
        }
        true
    }

    override fun clear() = synchronized(lock) { clearLocked() }

    override fun forget(rule: ProtectedTrafficRule) = synchronized(lock) {
        val selection = ProtectedTrafficConfiguration(rules = listOf(rule.copy(enabled = true)),
            enabledBuiltInGroups = setOfNotNull(rule.groupId))
        deadlines.keys.removeAll { key -> ProtectedTrafficPolicy().evaluate(selection, key.request()).evidence is ProtectedTrafficDecisionEvidence.Rule }
        mutableState.value = state.value.copy(generation = state.value.generation + 1, keys = deadlines.keys.toSet())
    }

    private fun AutomaticTlsFallbackKey.request() = ProtectedTrafficRequest(
        sourceApplication = sourceApplication, destinationHost = destination.value.takeUnless { destination.isIpLiteral },
        destinationAddress = destination.value.takeIf { destination.isIpLiteral }, port = port, transport = ProtectedTransportProtocol.TCP,
    )

    private fun clearLocked() {
        deadlines.clear()
        mutableState.value = state.value.copy(generation = state.value.generation + 1, keys = emptySet())
    }

    private fun prune() {
        val now = elapsedMillis()
        if (deadlines.entries.removeAll { it.value <= now }) publish()
    }

    private fun publish() { mutableState.value = state.value.copy(keys = deadlines.keys.toSet()) }
}
