package com.devuloopers.knet.connectivity.desktop.gateway

import com.devuloopers.knet.companion.model.CompanionSessionMessage
import com.devuloopers.knet.companion.model.CompanionSessionProtocol
import io.ktor.websocket.Frame
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.withTimeout

/** A blocked authenticated peer must release its session budget instead of pinning a policy/revocation writer. */
internal class ControlSessionWriteTimeout : Exception("Companion control peer did not drain its bounded outgoing queue.")

/** Bounded enqueue; a write timeout fails the session owner rather than silently cancelling one producer job. */
internal suspend fun sendCompanionControlMessage(outgoing: SendChannel<Frame>, message: CompanionSessionMessage) {
    try {
        withTimeout(2_000L) { outgoing.send(Frame.Text(CompanionSessionProtocol.encode(message))) }
    } catch (_: TimeoutCancellationException) { throw ControlSessionWriteTimeout() }
}
