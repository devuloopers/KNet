package com.devuloopers.knet.application.usecase.breakpoint

import com.devuloopers.knet.application.port.breakpoint.BreakpointBody
import com.devuloopers.knet.application.port.breakpoint.BreakpointCandidate
import com.devuloopers.knet.application.port.breakpoint.BreakpointProtocolRegistry
import com.devuloopers.knet.application.port.breakpoint.BreakpointRuleSuggestionInput
import com.devuloopers.knet.application.port.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.application.usecase.traffic.LoadTrafficExchangeDetailsResult
import com.devuloopers.knet.application.usecase.traffic.LoadTrafficExchangeDetailsUseCase
import com.devuloopers.knet.application.usecase.traffic.TrafficBodyPreview
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.BreakpointRule
import com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.absoluteUrl
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Protocol-neutral editor draft produced from one captured request.
 *
 * @property rule Canonical unsaved rule containing transport and suggested semantic criteria.
 * @property protocolValues Extension-owned values used to initialize the generic rule editor.
 */
public data class BreakpointRuleDraft(
    public val rule: BreakpointRule,
    public val protocolValues: List<ProtocolCriteriaValue>,
)

/** Result of preparing a breakpoint rule draft from a captured exchange. */
public sealed interface PrepareBreakpointRuleDraftResult {
    /**
     * A complete editor draft was prepared.
     *
     * @property draft Protocol-neutral rule editor state.
     */
    public data class Found(public val draft: BreakpointRuleDraft) : PrepareBreakpointRuleDraftResult

    /** The exchange disappeared and no matching pending candidate was supplied. */
    public data object Missing : PrepareBreakpointRuleDraftResult
}

/**
 * Builds a smart breakpoint-rule draft from canonical captured traffic.
 *
 * The use case loads at most the standard traffic preview budget, asks registered semantic protocol
 * extensions for a validated suggestion, and falls back to transport-only HTTP matching. Presentation
 * therefore does not know about GraphQL or any future protocol-specific criteria.
 *
 * @property loadTrafficExchangeDetailsUseCase Bounded canonical exchange loader.
 * @property protocolRegistry Registry of installed breakpoint protocol extensions.
 */
public class PrepareBreakpointRuleDraftUseCase(
    private val loadTrafficExchangeDetailsUseCase: LoadTrafficExchangeDetailsUseCase,
    private val protocolRegistry: BreakpointProtocolRegistry,
) {
    /**
     * Prepares an editor draft for [exchangeId].
     *
     * [pendingCandidate] supports an exchange that is currently paused before durable capture has
     * completed. It is accepted only when its exchange identity matches [exchangeId].
     *
     * @param exchangeId Stable captured exchange identity.
     * @param pendingCandidate Optional in-flight source for a not-yet-durable exchange.
     * @return A prepared draft or [PrepareBreakpointRuleDraftResult.Missing].
     */
    public suspend fun execute(
        exchangeId: ExchangeId,
        pendingCandidate: BreakpointCandidate? = null,
    ): PrepareBreakpointRuleDraftResult {
        val source = when (val loaded = loadTrafficExchangeDetailsUseCase.execute(exchangeId)) {
            is LoadTrafficExchangeDetailsResult.Found -> loaded.details.requestBody.toSource(
                request = loaded.details.exchange.request,
            )

            LoadTrafficExchangeDetailsResult.Missing -> pendingCandidate
                ?.takeIf { it.exchangeId == exchangeId }
                ?.toSource()
        } ?: return PrepareBreakpointRuleDraftResult.Missing

        val suggestedCriteria = protocolRegistry.suggestCriteria(
            BreakpointRuleSuggestionInput(
                request = source.request,
                requestBody = source.body,
                requestBodyComplete = source.bodyComplete,
            ),
        ) ?: ProtocolMatchCriteria.HttpDefault
        val endpointPattern = source.request.absoluteUrl()
            .substringBefore('#')
            .substringBefore('?')
            .ifBlank { "*" }
        val rule = BreakpointRule(
            id = newRuleId(),
            name = endpointPattern,
            urlPattern = endpointPattern,
            method = source.request.head.method,
            phase = BreakpointPhase.BOTH,
            enabled = true,
            protocolCriteria = suggestedCriteria,
        )
        return PrepareBreakpointRuleDraftResult.Found(
            BreakpointRuleDraft(
                rule = rule,
                protocolValues = protocolRegistry.editorValues(suggestedCriteria),
            ),
        )
    }

    private fun TrafficBodyPreview.toSource(request: HttpRequestSnapshot): DraftSource = when (this) {
        TrafficBodyPreview.Empty -> DraftSource(request, body = null, bodyComplete = true)
        is TrafficBodyPreview.Available -> DraftSource(
            request = request,
            body = BreakpointBody(chunk.copyBytes()),
            bodyComplete = chunk.endOfBody,
        )

        is TrafficBodyPreview.Unavailable,
        TrafficBodyPreview.ReadFailed -> DraftSource(request, body = null, bodyComplete = false)
    }

    private fun BreakpointCandidate.toSource(): DraftSource {
        val sourceBody = requestBody
        val retainedBody = sourceBody?.let {
            BreakpointBody(it.copyBytes(MAXIMUM_SUGGESTION_BODY_BYTES))
        }
        val bodyComplete = when {
            sourceBody == null -> requestObservedBodyBytes == 0L
            sourceBody.size > MAXIMUM_SUGGESTION_BODY_BYTES -> false
            else -> requestObservedBodyBytes == sourceBody.size.toLong()
        }
        return DraftSource(
            request = request,
            body = retainedBody,
            bodyComplete = bodyComplete,
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun newRuleId(): String = Uuid.random().toString()

    private data class DraftSource(
        val request: HttpRequestSnapshot,
        val body: BreakpointBody?,
        val bodyComplete: Boolean,
    )

    private companion object {
        const val MAXIMUM_SUGGESTION_BODY_BYTES: Int = 1_048_576
    }
}
