package com.devuloopers.knet.testingserver.graphql

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SubscriptionMapping
import org.springframework.stereotype.Controller
import kotlin.time.Duration.Companion.milliseconds

/**
 * GraphQL response used to verify operation-aware naming and structured body inspection.
 *
 * @property message Echoed or transformed content.
 * @property operation Resolver operation that produced the response.
 */
data class GraphQlEchoPayload(
    val message: String,
    val operation: String,
)

/**
 * GraphQL subscription item.
 *
 * @property sequence One-based event order.
 * @property message Caller-controlled event prefix combined with the sequence.
 */
data class GraphQlStreamEvent(
    val sequence: Int,
    val message: String,
)

/** Defines deterministic GraphQL queries, mutations, and subscriptions for KNet inspection. */
@Controller
class ProtocolGraphQlController {
    /**
     * Echoes an argument through a named GraphQL query.
     *
     * @param message Caller-provided query input.
     * @return Query response preserving the input.
     */
    @QueryMapping
    fun echo(@Argument message: String): GraphQlEchoPayload = GraphQlEchoPayload(
        message = message,
        operation = "query",
    )

    /**
     * Reverses text through a mutation to produce a visibly different response.
     *
     * @param message Caller-provided mutation input.
     * @return Mutation response containing reversed text.
     */
    @MutationMapping
    fun reverse(@Argument message: String): GraphQlEchoPayload = GraphQlEchoPayload(
        message = message.reversed(),
        operation = "mutation",
    )

    /**
     * Emits a finite GraphQL subscription stream over the configured WebSocket transport.
     *
     * @param message Prefix included in every emitted event.
     * @param count Event count clamped to the lab safety limit.
     * @param delayMillis Delay between events, clamped to ten seconds.
     * @return Cold bounded subscription stream.
     */
    @SubscriptionMapping
    fun ticker(
        @Argument message: String,
        @Argument count: Int,
        @Argument delayMillis: Int,
    ): Flow<GraphQlStreamEvent> = flow {
        val effectiveCount = count.coerceIn(1, MAX_EVENTS)
        val effectiveDelay = delayMillis.coerceIn(0, MAX_DELAY_MILLIS)
        repeat(effectiveCount) { index ->
            val sequence = index + 1
            emit(GraphQlStreamEvent(sequence = sequence, message = "$message-$sequence"))
            if (effectiveDelay > 0 && sequence < effectiveCount) {
                delay(effectiveDelay.milliseconds)
            }
        }
    }

    private companion object {
        const val MAX_EVENTS = 100
        const val MAX_DELAY_MILLIS = 10_000
    }
}
