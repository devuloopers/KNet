package com.devuloopers.knet.application.port.breakpoint

import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.BreakpointProtocolId
import com.devuloopers.knet.domain.rules.model.BreakpointRule
import com.devuloopers.knet.domain.rules.model.BreakpointTransportMatcher
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.HttpResponseSnapshot
import com.devuloopers.knet.traffic.model.absoluteUrl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Bounded immutable body value exposed to an authorized breakpoint editor. */
public class BreakpointBody(bytes: ByteArray) {
    private val content: ByteArray = bytes.copyOf()

    init {
        require(content.size <= 64 * 1024 * 1024) { "Breakpoint body exceeds the application hard limit." }
    }

    /** Number of editable bytes. */
    public val size: Int
        get() = content.size

    /** Returns a defensive copy owned by the caller. */
    public fun copyBytes(): ByteArray = content.copyOf()

    public override fun equals(other: Any?): Boolean =
        other is BreakpointBody && content.contentEquals(other.content)

    public override fun hashCode(): Int = content.contentHashCode()

    public override fun toString(): String = "BreakpointBody(size=$size)"
}

/** One engine-owned candidate presented to the application breakpoint coordinator. */
public data class BreakpointCandidate(
    public val exchangeId: ExchangeId,
    public val phase: BreakpointPhase,
    public val request: HttpRequestSnapshot,
    public val requestBody: BreakpointBody? = null,
    public val requestObservedBodyBytes: Long = requestBody?.size?.toLong() ?: 0L,
    public val response: HttpResponseSnapshot? = null,
    public val responseBody: BreakpointBody? = null,
    public val responseObservedBodyBytes: Long = responseBody?.size?.toLong() ?: 0L,
    public val startedAtEpochMillis: Long,
) {
    init {
        require(requestObservedBodyBytes >= 0L) { "Observed request body bytes must not be negative." }
        require(responseObservedBodyBytes >= 0L) { "Observed response body bytes must not be negative." }
        require(phase != BreakpointPhase.BOTH) { "A breakpoint candidate must have one concrete phase." }
        require(phase != BreakpointPhase.RESPONSE || response != null) {
            "A response breakpoint candidate requires response metadata."
        }
    }

    /** Total bytes retained while this candidate is pending. */
    public val retainedBytes: Long
        get() = (requestBody?.size ?: 0).toLong() + (responseBody?.size ?: 0).toLong()
}

/** Validated request replacement returned to the transport. */
public data class BreakpointRequestEdit(
    public val request: HttpRequestSnapshot,
    public val body: BreakpointBody?,
)

/** Validated response replacement returned to the transport. */
public data class BreakpointResponseEdit(
    public val response: HttpResponseSnapshot,
    public val body: BreakpointBody?,
)

/** Terminal decision for one matched breakpoint candidate. */
public sealed interface BreakpointDecision {
    /** Resume with the original message. */
    public data object ContinueUnchanged : BreakpointDecision

    /** Resume using an optional bounded replacement for the active phase. */
    public data class Resume(
        public val requestEdit: BreakpointRequestEdit? = null,
        public val responseEdit: BreakpointResponseEdit? = null,
    ) : BreakpointDecision

    /** Drop the exchange and close its transport. */
    public data object Drop : BreakpointDecision
}

/** Immutable pending record safe to expose to an authorized presentation. */
public data class PendingBreakpoint(
    public val id: String,
    public val ruleId: String,
    public val candidate: BreakpointCandidate,
)

/** Aggregate requirements read synchronously by the proxy pipeline at connection setup. */
public data class BreakpointRequirements(
    public val hasRequestRules: Boolean,
    public val hasResponseRules: Boolean,
    public val maxEditableBodyBytes: Int,
)

/** Independently configurable limits for the intentional breakpoint pause path. */
public data class BreakpointLimits(
    public val maxPendingConnections: Int = 32,
    public val maxPendingBytes: Long = 32L * 1024L * 1024L,
    public val maxEditableBodyBytes: Int = 10 * 1024 * 1024,
    public val maxTrackedProtocolExchanges: Int = 1_024,
    public val decisionTimeoutMillis: Long = 120_000L,
) {
    init {
        require(maxPendingConnections in 1..10_000) { "Pending breakpoint limit is invalid." }
        require(maxPendingBytes in 1L..(1024L * 1024L * 1024L)) { "Pending byte limit is invalid." }
        require(maxEditableBodyBytes in 1..(64 * 1024 * 1024)) { "Editable body limit is invalid." }
        require(maxTrackedProtocolExchanges in 1..100_000) {
            "Tracked protocol exchange limit is invalid."
        }
        require(decisionTimeoutMillis in 100L..3_600_000L) { "Breakpoint timeout is invalid." }
    }
}

/** Engine-facing application gate. It contains no Netty, persistence, or UI types. */
public interface BreakpointGate {
    /** Current immutable aggregation/body requirements. */
    public val requirements: StateFlow<BreakpointRequirements>

    /** Matches, admits, publishes, awaits, and terminally removes one candidate. */
    public suspend fun intercept(candidate: BreakpointCandidate): BreakpointDecision

    /** Cancels pending decisions for a disconnected exchange. */
    public fun cancelExchange(exchangeId: ExchangeId)
}

/** UI/control-facing application port for rules and pending decisions. */
public interface BreakpointControlPort {
    public val pendingBreakpoints: StateFlow<List<PendingBreakpoint>>
    public val isEnabled: StateFlow<Boolean>

    public fun replaceRules(rules: List<BreakpointRule>)
    public fun setEnabled(enabled: Boolean)
    public fun setDecisionTimeoutMillis(timeoutMillis: Long)
    public suspend fun resolve(pendingId: String, decision: BreakpointDecision): Boolean
    public suspend fun dropMatching(url: String, method: String): Int
    public suspend fun clear(): Int
}

/** Runtime-facing switch that prevents breakpoint suspension while capture is detached. */
public interface BreakpointCaptureAvailabilityPort {
    /**
     * Updates whether captured exchanges may enter breakpoint suspension.
     *
     * Disabling availability continues every pending decision unchanged. It deliberately does not
     * alter [BreakpointGate.requirements], so transport pipelines and client connections stay stable.
     */
    public suspend fun setCaptureAvailable(available: Boolean)
}

/**
 * Application-owned breakpoint state machine with compiled immutable rules and bounded pause state.
 */
public class BreakpointCoordinator(
    private val limits: BreakpointLimits = BreakpointLimits(),
    private val protocolRegistry: BreakpointProtocolRegistry = BreakpointProtocolRegistry(),
) : BreakpointGate, BreakpointControlPort, BreakpointCaptureAvailabilityPort {
    private val stateMutex = Mutex()
    private val compiledRules = MutableStateFlow<List<CompiledRule>>(emptyList())
    private val _pending = MutableStateFlow<List<PendingEntry>>(emptyList())
    private val pendingProjection = MutableStateFlow<List<PendingBreakpoint>>(emptyList())
    private val _enabled = MutableStateFlow(true)
    private val captureAvailable = MutableStateFlow(true)
    private val protocolObservations =
        MutableStateFlow<Map<ExchangeId, Map<BreakpointProtocolId, ProtocolObservation>>>(emptyMap())
    private val decisionTimeoutMillis = MutableStateFlow(limits.decisionTimeoutMillis)
    private val _requirements = MutableStateFlow(requirementsFor(emptyList(), enabled = true))

    override val pendingBreakpoints: StateFlow<List<PendingBreakpoint>> = pendingProjection.asStateFlow()

    override val isEnabled: StateFlow<Boolean> = _enabled.asStateFlow()
    override val requirements: StateFlow<BreakpointRequirements> = _requirements.asStateFlow()

    override fun replaceRules(rules: List<BreakpointRule>) {
        val compiled = rules.asSequence()
            .filter(BreakpointRule::enabled)
            .mapNotNull { rule ->
                protocolRegistry.compile(rule.protocolCriteria)?.let { protocolCriteria ->
                    CompiledRule(rule, protocolCriteria)
                }
            }
            .toList()
        compiledRules.value = compiled
        _requirements.value = requirementsFor(compiled, _enabled.value)
    }

    override fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        if (!enabled) protocolObservations.value = emptyMap()
        _requirements.value = requirementsFor(compiledRules.value, enabled)
    }

    override fun setDecisionTimeoutMillis(timeoutMillis: Long) {
        require(timeoutMillis in 100L..3_600_000L) { "Breakpoint timeout is invalid." }
        decisionTimeoutMillis.value = timeoutMillis
    }

    override suspend fun setCaptureAvailable(available: Boolean) {
        stateMutex.withLock {
            captureAvailable.value = available
            if (!available) {
                protocolObservations.value = emptyMap()
                val pending = _pending.value
                pending.forEach { entry ->
                    entry.decision.complete(BreakpointDecision.ContinueUnchanged)
                }
                publishEntries(emptyList())
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun intercept(candidate: BreakpointCandidate): BreakpointDecision {
        if (!_enabled.value || !captureAvailable.value) {
            if (candidate.phase == BreakpointPhase.RESPONSE) removeProtocolObservations(candidate.exchangeId)
            return BreakpointDecision.ContinueUnchanged
        }
        val rules = compiledRules.value
        val observations = observeProtocols(candidate, rules)
        val rule = rules.firstOrNull { compiled ->
            compiled.matchesTransport(candidate, candidate.phase) &&
                protocolRegistry.matches(
                    compiled.protocolCriteria,
                    observations[compiled.protocolCriteria.protocolId],
                )
        }
            ?: return BreakpointDecision.ContinueUnchanged
        if (candidate.requestObservedBodyBytes > limits.maxEditableBodyBytes ||
            candidate.responseObservedBodyBytes > limits.maxEditableBodyBytes
        ) {
            return BreakpointDecision.ContinueUnchanged
        }
        val entry = PendingEntry(
            public = PendingBreakpoint(Uuid.random().toString(), rule.definition.id, candidate),
            decision = CompletableDeferred(),
        )
        val admitted = stateMutex.withLock {
            val current = _pending.value
            val retained = current.sumOf { it.public.candidate.retainedBytes }
            if (!captureAvailable.value || current.size >= limits.maxPendingConnections ||
                retained + candidate.retainedBytes > limits.maxPendingBytes
            ) {
                false
            } else {
                publishEntries(current + entry)
                true
            }
        }
        if (!admitted) return BreakpointDecision.ContinueUnchanged

        val result = try {
            withTimeoutOrNull(decisionTimeoutMillis.value) { entry.decision.await() }
                ?: BreakpointDecision.ContinueUnchanged
        } finally {
            stateMutex.withLock {
                publishEntries(_pending.value.filterNot { it.public.id == entry.public.id })
            }
        }
        return result
    }

    override suspend fun resolve(pendingId: String, decision: BreakpointDecision): Boolean =
        stateMutex.withLock {
            if (!decision.within(limits.maxEditableBodyBytes)) return@withLock false
            _pending.value.firstOrNull {
                it.public.id == pendingId || it.public.candidate.exchangeId.value == pendingId
            }?.decision?.complete(decision) ?: false
        }

    override fun cancelExchange(exchangeId: ExchangeId) {
        removeProtocolObservations(exchangeId)
        _pending.value
            .filter { it.public.candidate.exchangeId == exchangeId }
            .forEach { it.decision.complete(BreakpointDecision.Drop) }
    }

    override suspend fun dropMatching(url: String, method: String): Int = stateMutex.withLock {
        val matches = _pending.value.filter { entry ->
            entry.public.candidate.request.head.method.token.equals(method, ignoreCase = true) &&
                entry.public.candidate.request.absoluteUrl().equals(url, ignoreCase = true)
        }
        matches.forEach { it.decision.complete(BreakpointDecision.Drop) }
        matches.size
    }

    override suspend fun clear(): Int = stateMutex.withLock {
        val entries = _pending.value
        entries.forEach { it.decision.complete(BreakpointDecision.Drop) }
        entries.size
    }

    private fun publishEntries(entries: List<PendingEntry>) {
        _pending.value = entries
        pendingProjection.value = entries.map(PendingEntry::public)
    }

    /**
     * Inspects each relevant protocol once and retains only compact request facts needed by a
     * response rule. Raw bodies remain candidate-owned and are never stored in this cache.
     */
    private fun observeProtocols(
        candidate: BreakpointCandidate,
        rules: List<CompiledRule>,
    ): Map<BreakpointProtocolId, ProtocolObservation> {
        val phaseRules = rules.filter { it.matchesTransport(candidate, candidate.phase) }
        val responseRules = if (candidate.phase == BreakpointPhase.REQUEST) {
            rules.filter { it.matchesTransport(candidate, BreakpointPhase.RESPONSE) }
        } else {
            emptyList()
        }
        val cached = if (candidate.phase == BreakpointPhase.RESPONSE) {
            removeProtocolObservations(candidate.exchangeId)
        } else {
            emptyMap()
        }
        val relevantProtocolIds = (phaseRules + responseRules)
            .asSequence()
            .map { it.protocolCriteria.protocolId }
            .filterNot { it == BreakpointProtocolId.HTTP }
            .distinct()
            .toList()
        if (relevantProtocolIds.isEmpty()) return cached

        val observed = relevantProtocolIds.mapNotNull { protocolId ->
            val requestObservation = cached[protocolId]
            val observation = protocolRegistry.inspect(
                protocolId = protocolId,
                input = ProtocolInspectionInput(candidate, requestObservation),
            ) ?: requestObservation
            observation?.let { protocolId to it }
        }.toMap()

        if (candidate.phase == BreakpointPhase.REQUEST && responseRules.isNotEmpty()) {
            val responseProtocolIds = responseRules.mapTo(mutableSetOf()) {
                it.protocolCriteria.protocolId
            }
            val responseObservations = observed.filterKeys { it in responseProtocolIds }
            retainProtocolObservations(candidate.exchangeId, responseObservations)
        }
        return observed
    }

    /** Retains bounded immutable observation maps without a JVM concurrent collection. */
    private fun retainProtocolObservations(
        exchangeId: ExchangeId,
        observations: Map<BreakpointProtocolId, ProtocolObservation>,
    ) {
        if (observations.isEmpty()) return
        protocolObservations.update { current ->
            when {
                exchangeId in current -> current + (exchangeId to observations)
                current.size >= limits.maxTrackedProtocolExchanges -> current
                else -> current + (exchangeId to observations)
            }
        }
    }

    /** Atomically removes and returns compact observations for one completed exchange. */
    private fun removeProtocolObservations(
        exchangeId: ExchangeId,
    ): Map<BreakpointProtocolId, ProtocolObservation> {
        var removed: Map<BreakpointProtocolId, ProtocolObservation> = emptyMap()
        protocolObservations.update { current ->
            removed = current[exchangeId].orEmpty()
            current - exchangeId
        }
        return removed
    }

    private fun requirementsFor(rules: List<CompiledRule>, enabled: Boolean): BreakpointRequirements =
        BreakpointRequirements(
            hasRequestRules = enabled && rules.any { it.includes(BreakpointPhase.REQUEST) },
            hasResponseRules = enabled && rules.any { it.includes(BreakpointPhase.RESPONSE) },
            maxEditableBodyBytes = limits.maxEditableBodyBytes,
        )

    private data class PendingEntry(
        val public: PendingBreakpoint,
        val decision: CompletableDeferred<BreakpointDecision>,
    )

    private class CompiledRule(
        val definition: BreakpointRule,
        val protocolCriteria: CompiledProtocolCriteria,
    ) {
        private val transportMatcher = BreakpointTransportMatcher(definition)

        fun includes(phase: BreakpointPhase): Boolean = transportMatcher.includes(phase)

        fun matchesTransport(candidate: BreakpointCandidate, phase: BreakpointPhase): Boolean =
            transportMatcher.matches(
                url = candidate.request.absoluteUrl(),
                method = candidate.request.head.method.token,
                phase = phase,
            )
    }
}

private fun BreakpointDecision.within(limit: Int): Boolean = when (this) {
    BreakpointDecision.ContinueUnchanged,
    BreakpointDecision.Drop -> true
    is BreakpointDecision.Resume ->
        (requestEdit?.body?.size ?: 0) <= limit && (responseEdit?.body?.size ?: 0) <= limit
}
