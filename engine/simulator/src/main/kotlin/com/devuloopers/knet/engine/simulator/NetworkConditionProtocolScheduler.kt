package com.devuloopers.knet.engine.simulator

import com.devuloopers.knet.domain.networkconditions.EffectiveNetworkCondition
import com.devuloopers.knet.domain.networkconditions.NetworkFailureBehavior
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.nanoseconds

/** Result of scheduling one complete protocol-safe message or event. */
sealed interface NetworkConditionProtocolScheduleResult {
    /** The caller may forward the unchanged protocol unit now. */
    data object Forward : NetworkConditionProtocolScheduleResult

    /** The configured fault requires the upgraded connection to close. */
    data class DropConnection(
        val behavior: NetworkFailureBehavior,
    ) : NetworkConditionProtocolScheduleResult

    /** The configured timeout intentionally withholds the protocol unit until lifecycle cancellation. */
    data object Timeout : NetworkConditionProtocolScheduleResult
}

/**
 * Coroutine adapter that applies the shared bounded condition engine to complete protocol units.
 *
 * It reserves aggregate bandwidth and queue bytes before suspension, releases them exactly once after delivery,
 * and abandons them on cancellation. Protocol codecs remain responsible for framing and connection termination.
 */
class NetworkConditionProtocolScheduler(
    private val engine: NetworkConditionEngine,
) {
    /** Opens one telemetry flow owned by an upgraded protocol connection. */
    fun openFlow(): AutoCloseable = engine.openFlow()

    /** Applies [effective] to one directionally ordered unit containing [bytes] wire bytes. */
    suspend fun schedule(
        effective: EffectiveNetworkCondition?,
        direction: NetworkConditionDirection,
        bytes: Int,
        sequence: Long,
    ): NetworkConditionProtocolScheduleResult = when (
        val plan = engine.plan(effective, direction, bytes, sequence)
    ) {
        NetworkConditionPlan.PassThrough -> {
            engine.recordForwarded(direction, bytes)
            NetworkConditionProtocolScheduleResult.Forward
        }

        is NetworkConditionPlan.Fail -> when (plan.behavior) {
            NetworkFailureBehavior.Timeout -> NetworkConditionProtocolScheduleResult.Timeout
            else -> NetworkConditionProtocolScheduleResult.DropConnection(plan.behavior)
        }

        is NetworkConditionPlan.Delay -> {
            if (!engine.tryQueue(bytes)) {
                engine.recordFaultedFlow()
                return NetworkConditionProtocolScheduleResult.DropConnection(NetworkFailureBehavior.ResetFlow)
            }
            try {
                delay(plan.nanoseconds.nanoseconds)
                engine.releaseQueued(bytes)
                engine.recordForwarded(direction, bytes)
                NetworkConditionProtocolScheduleResult.Forward
            } catch (failure: Throwable) {
                engine.abandon(bytes)
                throw failure
            }
        }
    }
}
