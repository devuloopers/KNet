package com.devuloopers.knet.ui.desktop.httppanel.usecase

import com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType
import com.devuloopers.knet.domain.payload.PayloadStrategyRegistry
import com.devuloopers.knet.domain.payload.StructuredPayloadState
import com.devuloopers.knet.ui.desktop.httppanel.model.GraphQlState
import com.devuloopers.knet.ui.desktop.httppanel.model.RequestBodyMode
import com.devuloopers.knet.ui.desktop.httppanel.model.RequestBodyState

/**
 * Presentation UseCase responsible for synchronizing [RequestBodyState] mode transitions,
 * GraphQL state parsing, and payload serialization dynamically via [PayloadStrategyRegistry].
 *
 * Provides a single, 100% reusable, and testable source of truth for request body state transitions
 * across any ViewModel (API Studio, Traffic Inspector, Mock Server, Scripting Engine).
 *
 * @param strategyRegistry Injected [PayloadStrategyRegistry] resolving payload strategies dynamically by [RequestBodyType].
 */
class SyncBodyStateUseCase(
    private val strategyRegistry: PayloadStrategyRegistry
) {

    /**
     * Updates [RequestBodyState.mode] and automatically hydrates payload state models (such as [GraphQlState])
     * if switching to a structured payload mode (e.g. [RequestBodyMode.GRAPHQL]).
     */
    fun switchMode(currentState: RequestBodyState, targetMode: RequestBodyMode): RequestBodyState {
        if (currentState.mode == targetMode) return currentState

        val updatedGraphQlState =
            if (
                targetMode == RequestBodyMode.GRAPHQL && currentState.graphQlState.queryText.isEmpty() &&
                currentState.payloadText.isNotEmpty()) {
                parseGraphQlState(currentState.payloadText
                )
            } else {
                currentState.graphQlState
            }

        return currentState.copy(mode = targetMode, graphQlState = updatedGraphQlState)
    }

    /**
     * Updates structured [GraphQlState], automatically serializing it back into transport [RequestBodyState.payloadText].
     */
    fun updateGraphQlState(currentState: RequestBodyState, newGraphQlState: GraphQlState): RequestBodyState {
        val serializedPayload = strategyRegistry.serialize(RequestBodyType.GRAPHQL, newGraphQlState.payload)

        return currentState.copy(
            graphQlState = newGraphQlState,
            payloadText = serializedPayload
        )
    }

    /**
     * Ensures [graphQlState] is hydrated from [payloadText] if currently in [RequestBodyMode.GRAPHQL].
     */
    fun ensureHydrated(currentState: RequestBodyState): RequestBodyState {
        if (currentState.mode == RequestBodyMode.GRAPHQL && currentState.graphQlState.queryText.isEmpty() && currentState.payloadText.isNotEmpty()) {
            val parsedState = parseGraphQlState(currentState.payloadText)
            return currentState.copy(graphQlState = parsedState)
        }
        return currentState
    }

    private fun parseGraphQlState(payload: String): GraphQlState {
        return when (val parsed = strategyRegistry.parse(RequestBodyType.GRAPHQL, payload)) {
            is StructuredPayloadState.GraphQL -> GraphQlState(payload = parsed)

            is StructuredPayloadState.RawText -> GraphQlState(
                payload = StructuredPayloadState.GraphQL(queryText = parsed.content)
            )
        }
    }
}
