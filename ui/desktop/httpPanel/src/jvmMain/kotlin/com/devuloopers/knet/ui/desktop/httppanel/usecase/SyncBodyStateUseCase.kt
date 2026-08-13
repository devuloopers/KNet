package com.devuloopers.knet.ui.desktop.httppanel.usecase

import com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType
import com.devuloopers.knet.domain.payload.PayloadMapper
import com.devuloopers.knet.domain.payload.PayloadMapperRegistry
import com.devuloopers.knet.ui.desktop.httppanel.model.BodyMode
import com.devuloopers.knet.ui.desktop.httppanel.model.BodyState
import com.devuloopers.knet.ui.desktop.httppanel.model.GraphQlState

/**
 * Presentation UseCase responsible for synchronizing [BodyState] mode transitions,
 * GraphQL state parsing, and payload serialization dynamically via [PayloadMapperRegistry].
 *
 * Provides a single, 100% reusable, and testable source of truth for body state transitions
 * across any ViewModel (API Studio, Traffic Inspector, Mock Server, Scripting Engine).
 *
 * @param mapperRegistry Injected [PayloadMapperRegistry] resolving payload mappers dynamically by [RequestBodyType].
 */
public class SyncBodyStateUseCase(
    private val mapperRegistry: PayloadMapperRegistry
) {

    /**
     * Updates [BodyState.mode] and automatically hydrates payload state models (such as [GraphQlState])
     * if switching to a structured payload mode (e.g. [BodyMode.GRAPHQL]).
     */
    public fun switchMode(currentState: BodyState, targetMode: BodyMode): BodyState {
        if (currentState.mode == targetMode) return currentState

        val updatedGraphQlState = if (targetMode == BodyMode.GRAPHQL && currentState.graphQlState.queryText.isEmpty() && currentState.payloadText.isNotEmpty()) {
            parseGraphQlState(currentState.payloadText)
        } else {
            currentState.graphQlState
        }

        return currentState.copy(mode = targetMode, graphQlState = updatedGraphQlState)
    }

    /**
     * Updates structured [GraphQlState], automatically serializing it back into transport [BodyState.payloadText].
     */
    public fun updateGraphQlState(currentState: BodyState, newGraphQlState: GraphQlState): BodyState {
        val mapper = mapperRegistry.getMapper<GraphQlState>(RequestBodyType.GRAPHQL)
        val serializedPayload = mapper?.serializePayload(newGraphQlState) ?: currentState.payloadText

        return currentState.copy(
            graphQlState = newGraphQlState,
            payloadText = serializedPayload
        )
    }

    /**
     * Ensures [graphQlState] is hydrated from [payloadText] if currently in [BodyMode.GRAPHQL].
     */
    public fun ensureHydrated(currentState: BodyState): BodyState {
        if (currentState.mode == BodyMode.GRAPHQL && currentState.graphQlState.queryText.isEmpty() && currentState.payloadText.isNotEmpty()) {
            val parsedState = parseGraphQlState(currentState.payloadText)
            return currentState.copy(graphQlState = parsedState)
        }
        return currentState
    }

    private fun parseGraphQlState(payloadText: String): GraphQlState {
        val mapper = mapperRegistry.getMapper<GraphQlState>(RequestBodyType.GRAPHQL)
        return mapper?.parsePayload(payloadText) ?: GraphQlState()
    }
}
