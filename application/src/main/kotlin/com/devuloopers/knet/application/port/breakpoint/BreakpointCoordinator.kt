package com.devuloopers.knet.application.port.breakpoint

import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.BreakpointProtocolId
import com.devuloopers.knet.domain.rules.model.BreakpointRule
import com.devuloopers.knet.domain.rules.model.BreakpointTransportMatcher
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.traffic.model.http.HeaderField
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
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

    /**
     * Returns a defensive prefix whose size does not exceed [maximumBytes].
     *
     * @param maximumBytes Positive upper bound for the returned copy.
     * @return Independent byte array containing at most the requested prefix.
     * @throws IllegalArgumentException when [maximumBytes] is not positive.
     */
    public fun copyBytes(maximumBytes: Int): ByteArray {
        require(maximumBytes > 0) { "Maximum breakpoint body copy size must be positive." }
        return content.copyOf(minOf(content.size, maximumBytes))
    }

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
    public val retainedTransportBytes: Long = 0L,
    public val origin: TrafficOrigin = TrafficOrigin.ProxyClient,
    public val startedAtEpochMillis: Long,
) {
    init {
        require(requestObservedBodyBytes >= 0L) { "Observed request body bytes must not be negative." }
        require(responseObservedBodyBytes >= 0L) { "Observed response body bytes must not be negative." }
        require(retainedTransportBytes >= 0L) { "Retained transport bytes must not be negative." }
        require(phase != BreakpointPhase.BOTH) { "A breakpoint candidate must have one concrete phase." }
        require(phase != BreakpointPhase.RESPONSE || response != null) {
            "A response breakpoint candidate requires response metadata."
        }
    }

    /** Total bytes retained while this candidate is pending. */
    public val retainedBytes: Long
        get() = (requestBody?.size ?: 0).toLong() +
                (responseBody?.size ?: 0).toLong() +
                retainedTransportBytes
}

/** Explicit ownership intent for an intercepted message body. */
public sealed interface BreakpointBodyEdit {
    /** Preserve the original transport bytes without decoding or copying them into an edit. */
    public data object Unchanged : BreakpointBodyEdit

    /** Replace the original body, including with an intentionally empty body. */
    public data class Replace(
        public val body: BreakpointBody,
    ) : BreakpointBodyEdit
}

/** Validated request replacement returned to the transport. */
public data class BreakpointRequestEdit(
    public val request: HttpRequestSnapshot,
    public val body: BreakpointBodyEdit = BreakpointBodyEdit.Unchanged,
)

/** Validated response replacement returned to the transport. */
public data class BreakpointResponseEdit(
    public val response: HttpResponseSnapshot,
    public val body: BreakpointBodyEdit = BreakpointBodyEdit.Unchanged,
)

/** Terminal decision for one matched breakpoint candidate. */
public sealed interface BreakpointDecision {
    /** Resume with the original message. */
    public data object ContinueUnchanged : BreakpointDecision

    /** Resume a request-phase interception with a validated request edit. */
    public data class ResumeRequest(
        public val edit: BreakpointRequestEdit,
    ) : BreakpointDecision

    /** Resume a response-phase interception with a validated response edit. */
    public data class ResumeResponse(
        public val edit: BreakpointResponseEdit,
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
    public val maxEditedHeaderCount: Int = 512,
    public val maxEditedHeaderBytes: Int = 1024 * 1024,
    public val maxTrackedProtocolExchanges: Int = 1_024,
    public val decisionTimeoutMillis: Long = 120_000L,
) {
    init {
        require(maxPendingConnections in 1..10_000) { "Pending breakpoint limit is invalid." }
        require(maxPendingBytes in 1L..(1024L * 1024L * 1024L)) { "Pending byte limit is invalid." }
        require(maxEditableBodyBytes in 1..(64 * 1024 * 1024)) { "Editable body limit is invalid." }
        require(maxEditedHeaderCount in 1..10_000) { "Edited header count limit is invalid." }
        require(maxEditedHeaderBytes in 1..(16 * 1024 * 1024)) { "Edited header byte limit is invalid." }
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

    /** Returns whether any enabled rule can match this request at [phase] before body inspection. */
    public fun mayIntercept(request: HttpRequestSnapshot, phase: BreakpointPhase): Boolean

    /** Matches, admits, publishes, awaits, and terminally removes one candidate. */
    public suspend fun intercept(candidate: BreakpointCandidate): BreakpointDecision

    /** Cancels pending decisions and releases protocol observations for a disconnected exchange. */
    public fun cancelExchange(exchangeId: ExchangeId)

    /** Releases protocol observations after an exchange completes without a full response candidate. */
    public fun releaseExchange(exchangeId: ExchangeId)
}

/** UI/control-facing application port for rules and pending decisions. */
public interface BreakpointControlPort {
    public val pendingBreakpoints: StateFlow<List<PendingBreakpoint>>
    public val isEnabled: StateFlow<Boolean>

    public fun replaceRules(rules: List<BreakpointRule>)
    public suspend fun setEnabled(enabled: Boolean)
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
) : BreakpointGate,
    BreakpointControlPort,
    BreakpointCaptureAvailabilityPort,
    ProtocolMessageBreakpointGate,
    ProtocolMessageBreakpointControlPort {
    private val stateMutex = Mutex()
    private val compiledRules = MutableStateFlow<List<CompiledRule>>(emptyList())
    private val _pending = MutableStateFlow<List<PendingEntry>>(emptyList())
    private val pendingProjection = MutableStateFlow<List<PendingBreakpoint>>(emptyList())
    private val _pendingProtocolMessages = MutableStateFlow<List<PendingMessageEntry>>(emptyList())
    private val pendingProtocolMessageProjection =
        MutableStateFlow<List<PendingProtocolMessageBreakpoint>>(emptyList())
    private val _enabled = MutableStateFlow(true)
    private val captureAvailable = MutableStateFlow(true)
    private val protocolObservations =
        MutableStateFlow<Map<ExchangeId, Map<BreakpointProtocolId, ProtocolObservation>>>(emptyMap())
    private val decisionTimeoutMillis = MutableStateFlow(limits.decisionTimeoutMillis)
    private val _requirements = MutableStateFlow(requirementsFor(emptyList(), enabled = true))

    override val pendingBreakpoints: StateFlow<List<PendingBreakpoint>> = pendingProjection.asStateFlow()
    override val pendingProtocolMessages: StateFlow<List<PendingProtocolMessageBreakpoint>> =
        pendingProtocolMessageProjection.asStateFlow()

    override val isEnabled: StateFlow<Boolean> = _enabled.asStateFlow()
    override val requirements: StateFlow<BreakpointRequirements> = _requirements.asStateFlow()

    override fun mayIntercept(request: HttpRequestSnapshot, phase: BreakpointPhase): Boolean =
        _enabled.value && captureAvailable.value && compiledRules.value.any { compiled ->
            compiled.interceptionUnit == BreakpointInterceptionUnit.HTTP_EXCHANGE &&
                    compiled.matchesTransport(request, phase)
        }

    override fun mayInterceptMessage(
        request: HttpRequestSnapshot,
        protocolRoute: List<BreakpointProtocolId>,
        direction: TrafficDirection,
    ): Boolean {
        require(protocolRoute.isNotEmpty()) { "A protocol message route must not be empty." }
        if (!_enabled.value || !captureAvailable.value) return false
        val phase = direction.toBreakpointPhase()
        return compiledRules.value.any { compiled ->
            compiled.interceptionUnit == BreakpointInterceptionUnit.PROTOCOL_MESSAGE &&
                    compiled.protocolCriteria.protocolId in protocolRoute &&
                    compiled.matchesTransport(request, phase)
        }
    }

    override fun replaceRules(rules: List<BreakpointRule>) {
        val compiled = rules.asSequence()
            .filter(BreakpointRule::enabled)
            .sortedWith(compareBy(BreakpointRule::priority, BreakpointRule::id))
            .mapNotNull { rule ->
                protocolRegistry.compile(rule.protocolCriteria)?.let { protocolCriteria ->
                    CompiledRule(
                        definition = rule,
                        protocolCriteria = protocolCriteria,
                        interceptionUnit = protocolRegistry.interceptionUnit(protocolCriteria.protocolId)
                            ?: BreakpointInterceptionUnit.HTTP_EXCHANGE,
                    )
                }
            }
            .toList()
        compiledRules.value = compiled
        _requirements.value = requirementsFor(compiled, _enabled.value)
    }

    override suspend fun setEnabled(enabled: Boolean) {
        stateMutex.withLock {
            _enabled.value = enabled
            if (!enabled) {
                protocolObservations.value = emptyMap()
                val pending = _pending.value
                pending.forEach { entry ->
                    entry.decision.complete(BreakpointDecision.ContinueUnchanged)
                }
                publishEntries(emptyList())
                val pendingMessages = _pendingProtocolMessages.value
                pendingMessages.forEach { entry ->
                    entry.decision.complete(ProtocolMessageBreakpointDecision.ContinueUnchanged)
                }
                publishMessageEntries(emptyList())
            }
            _requirements.value = requirementsFor(compiledRules.value, enabled)
        }
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
                val pendingMessages = _pendingProtocolMessages.value
                pendingMessages.forEach { entry ->
                    entry.decision.complete(ProtocolMessageBreakpointDecision.ContinueUnchanged)
                }
                publishMessageEntries(emptyList())
            }
        }
    }

    override suspend fun intercept(candidate: BreakpointCandidate): BreakpointDecision {
        if (!_enabled.value || !captureAvailable.value) {
            if (candidate.phase == BreakpointPhase.RESPONSE) removeProtocolObservations(candidate.exchangeId)
            return BreakpointDecision.ContinueUnchanged
        }
        if (candidate.requestObservedBodyBytes > limits.maxEditableBodyBytes ||
            candidate.responseObservedBodyBytes > limits.maxEditableBodyBytes
        ) {
            if (candidate.phase == BreakpointPhase.RESPONSE) removeProtocolObservations(candidate.exchangeId)
            return BreakpointDecision.ContinueUnchanged
        }
        val rules = compiledRules.value.filter {
            it.interceptionUnit == BreakpointInterceptionUnit.HTTP_EXCHANGE
        }
        val observations = observeProtocols(candidate, rules)
        val rule = rules.firstOrNull { compiled ->
            compiled.matchesTransport(candidate, candidate.phase) &&
                    protocolRegistry.matches(
                        compiled.protocolCriteria,
                        observations[compiled.protocolCriteria.protocolId],
                    )
        }
            ?: return BreakpointDecision.ContinueUnchanged
        val entry = PendingEntry(
            public = PendingBreakpoint(Uuid.random().toString(), rule.definition.id, candidate),
            decision = CompletableDeferred(),
        )
        val admitted = stateMutex.withLock {
            val current = _pending.value
            val retained = current.sumOf { it.public.candidate.retainedBytes }
            if (!_enabled.value || !captureAvailable.value || current.size >= limits.maxPendingConnections ||
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

    override suspend fun interceptMessage(
        candidate: ProtocolMessageBreakpointCandidate,
    ): ProtocolMessageBreakpointDecision {
        if (!_enabled.value || !captureAvailable.value) {
            return ProtocolMessageBreakpointDecision.ContinueUnchanged
        }
        if (candidate.body.size > limits.maxEditableBodyBytes) {
            return ProtocolMessageBreakpointDecision.ContinueUnchanged
        }
        val rules = compiledRules.value.filter { compiled ->
            compiled.interceptionUnit == BreakpointInterceptionUnit.PROTOCOL_MESSAGE &&
                    compiled.protocolCriteria.protocolId in candidate.protocolRoute &&
                    compiled.matchesTransport(candidate.request, candidate.phase)
        }
        val inspectionInput = ProtocolMessageInspectionInput(
            exchangeId = candidate.exchangeId,
            request = candidate.request,
            messageId = candidate.messageId,
            kind = candidate.kind,
            negotiatedSubprotocol = candidate.negotiatedSubprotocol,
            direction = candidate.direction,
            sequence = candidate.sequence,
            declaredBytes = candidate.declaredBytes,
            compressed = candidate.compressed,
            compressionEncoding = candidate.compressionEncoding,
            body = candidate.body,
        )
        val observations = candidate.protocolRoute.associateWith { protocolId ->
            protocolRegistry.inspectMessage(protocolId, inspectionInput)
        }
        val rule = rules.firstOrNull { compiled ->
            protocolRegistry.matches(
                compiled.protocolCriteria,
                observations[compiled.protocolCriteria.protocolId],
            )
        } ?: return ProtocolMessageBreakpointDecision.ContinueUnchanged

        val entry = PendingMessageEntry(
            public = PendingProtocolMessageBreakpoint(
                id = Uuid.random().toString(),
                ruleId = rule.definition.id,
                matchedProtocolId = rule.protocolCriteria.protocolId,
                candidate = candidate,
            ),
            decision = CompletableDeferred(),
        )
        val admitted = stateMutex.withLock {
            val httpPending = _pending.value
            val messagePending = _pendingProtocolMessages.value
            val retained = httpPending.sumOf { it.public.candidate.retainedBytes } +
                    messagePending.sumOf { it.public.candidate.retainedBytes }
            if (!_enabled.value || !captureAvailable.value ||
                httpPending.size + messagePending.size >= limits.maxPendingConnections ||
                retained + candidate.retainedBytes > limits.maxPendingBytes
            ) {
                false
            } else {
                publishMessageEntries(messagePending + entry)
                true
            }
        }
        if (!admitted) return ProtocolMessageBreakpointDecision.ContinueUnchanged

        return try {
            withTimeoutOrNull(decisionTimeoutMillis.value) { entry.decision.await() }
                ?: ProtocolMessageBreakpointDecision.ContinueUnchanged
        } finally {
            stateMutex.withLock {
                publishMessageEntries(
                    _pendingProtocolMessages.value.filterNot { it.public.id == entry.public.id },
                )
            }
        }
    }

    override suspend fun resolve(pendingId: String, decision: BreakpointDecision): Boolean =
        stateMutex.withLock {
            val pending = _pending.value.firstOrNull {
                it.public.id == pendingId || it.public.candidate.exchangeId.value == pendingId
            } ?: return@withLock false
            if (!decision.validFor(pending.public.candidate.phase, limits)) {
                return@withLock false
            }
            pending.decision.complete(decision)
        }

    override suspend fun resolveProtocolMessage(
        pendingId: String,
        decision: ProtocolMessageBreakpointDecision,
    ): Boolean = stateMutex.withLock {
        val pending = _pendingProtocolMessages.value.firstOrNull { entry ->
            entry.public.id == pendingId || entry.public.candidate.messageId.value == pendingId
        } ?: return@withLock false
        if (decision is ProtocolMessageBreakpointDecision.Replace &&
            decision.body.size > limits.maxEditableBodyBytes
        ) {
            return@withLock false
        }
        if (decision is ProtocolMessageBreakpointDecision.Replace &&
            !protocolRegistry.validateMessageReplacement(
                protocolId = pending.public.matchedProtocolId,
                input = pending.public.candidate.toInspectionInput(),
                replacement = decision.body,
            )
        ) return@withLock false
        pending.decision.complete(decision)
    }

    override fun cancelExchange(exchangeId: ExchangeId) {
        removeProtocolObservations(exchangeId)
        _pending.value
            .filter { it.public.candidate.exchangeId == exchangeId }
            .forEach { it.decision.complete(BreakpointDecision.Drop) }
        cancelProtocolMessages(exchangeId)
    }

    override fun cancelProtocolMessages(exchangeId: ExchangeId) {
        protocolRegistry.releaseMessages(exchangeId)
        _pendingProtocolMessages.value
            .filter { it.public.candidate.exchangeId == exchangeId }
            .forEach { it.decision.complete(ProtocolMessageBreakpointDecision.DropStream) }
    }

    override fun releaseExchange(exchangeId: ExchangeId) {
        removeProtocolObservations(exchangeId)
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
        val messageEntries = _pendingProtocolMessages.value
        entries.forEach { it.decision.complete(BreakpointDecision.Drop) }
        messageEntries.forEach { it.decision.complete(ProtocolMessageBreakpointDecision.DropStream) }
        entries.size + messageEntries.size
    }

    private fun publishEntries(entries: List<PendingEntry>) {
        _pending.value = entries
        pendingProjection.value = entries.map(PendingEntry::public)
    }

    private fun publishMessageEntries(entries: List<PendingMessageEntry>) {
        _pendingProtocolMessages.value = entries
        pendingProtocolMessageProjection.value = entries.map(PendingMessageEntry::public)
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
                current.size >= limits.maxTrackedProtocolExchanges ->
                    (current - current.keys.first()) + (exchangeId to observations)

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
            hasRequestRules = enabled && rules.any {
                it.interceptionUnit == BreakpointInterceptionUnit.HTTP_EXCHANGE &&
                        it.includes(BreakpointPhase.REQUEST)
            },
            hasResponseRules = enabled && rules.any {
                it.interceptionUnit == BreakpointInterceptionUnit.HTTP_EXCHANGE &&
                        it.includes(BreakpointPhase.RESPONSE)
            },
            maxEditableBodyBytes = limits.maxEditableBodyBytes,
        )

    private data class PendingEntry(
        val public: PendingBreakpoint,
        val decision: CompletableDeferred<BreakpointDecision>,
    )

    private data class PendingMessageEntry(
        val public: PendingProtocolMessageBreakpoint,
        val decision: CompletableDeferred<ProtocolMessageBreakpointDecision>,
    )

    private fun ProtocolMessageBreakpointCandidate.toInspectionInput(): ProtocolMessageInspectionInput =
        ProtocolMessageInspectionInput(
            exchangeId = exchangeId,
            request = request,
            messageId = messageId,
            kind = kind,
            negotiatedSubprotocol = negotiatedSubprotocol,
            direction = direction,
            sequence = sequence,
            declaredBytes = declaredBytes,
            compressed = compressed,
            compressionEncoding = compressionEncoding,
            body = body,
        )

    private class CompiledRule(
        val definition: BreakpointRule,
        val protocolCriteria: CompiledProtocolCriteria,
        val interceptionUnit: BreakpointInterceptionUnit,
    ) {
        private val transportMatcher = BreakpointTransportMatcher(definition)

        fun includes(phase: BreakpointPhase): Boolean = transportMatcher.includes(phase)

        fun matchesTransport(candidate: BreakpointCandidate, phase: BreakpointPhase): Boolean =
            matchesTransport(candidate.request, phase)

        fun matchesTransport(request: HttpRequestSnapshot, phase: BreakpointPhase): Boolean =
            transportMatcher.matches(
                url = request.absoluteUrl(),
                method = request.head.method.token,
                phase = phase,
            )
    }

}

private fun TrafficDirection.toBreakpointPhase(): BreakpointPhase = when (this) {
    TrafficDirection.CLIENT_TO_SERVER -> BreakpointPhase.REQUEST
    TrafficDirection.SERVER_TO_CLIENT -> BreakpointPhase.RESPONSE
}

private fun BreakpointDecision.validFor(phase: BreakpointPhase, limits: BreakpointLimits): Boolean = when (this) {
    BreakpointDecision.ContinueUnchanged,
    BreakpointDecision.Drop -> true

    is BreakpointDecision.ResumeRequest ->
        phase == BreakpointPhase.REQUEST &&
                edit.body.within(limits.maxEditableBodyBytes) &&
                edit.request.head.headers.within(limits)

    is BreakpointDecision.ResumeResponse ->
        phase == BreakpointPhase.RESPONSE &&
                edit.body.within(limits.maxEditableBodyBytes) &&
                edit.response.head.headers.within(limits)
}

private fun BreakpointBodyEdit.within(limit: Int): Boolean = when (this) {
    BreakpointBodyEdit.Unchanged -> true
    is BreakpointBodyEdit.Replace -> body.size <= limit
}

private fun List<HeaderField>.within(limits: BreakpointLimits): Boolean =
    size <= limits.maxEditedHeaderCount && sumOf { header ->
        header.name.value.encodeToByteArray().size.toLong() +
                header.value.encodeToByteArray().size.toLong()
    } <= limits.maxEditedHeaderBytes.toLong()
