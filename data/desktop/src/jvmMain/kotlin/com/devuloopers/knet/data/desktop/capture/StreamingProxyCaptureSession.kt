package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.application.port.traffic.BodyChunkReservation
import com.devuloopers.knet.application.port.traffic.CaptureIngressLimits
import com.devuloopers.knet.application.port.traffic.CaptureIngressPort
import com.devuloopers.knet.application.port.traffic.CapturePublishResult
import com.devuloopers.knet.engine.proxy.capture.ProxyBodyReservation
import com.devuloopers.knet.engine.proxy.capture.ProxyCaptureConnectionMetadata
import com.devuloopers.knet.engine.proxy.capture.ProxyCaptureSink
import com.devuloopers.knet.engine.proxy.capture.ProxyConnectionCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyMessageCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyMessageCaptureMetadata
import com.devuloopers.knet.traffic.id.BodyId
import com.devuloopers.knet.traffic.id.CaptureSessionId
import com.devuloopers.knet.traffic.id.ConnectionId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.ProtocolMessageId
import com.devuloopers.knet.traffic.id.StreamId
import com.devuloopers.knet.traffic.model.CaptureEvent
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficOrigin
import com.devuloopers.knet.traffic.model.body.BodyCaptureOutcome
import com.devuloopers.knet.traffic.model.body.BodyFailure
import com.devuloopers.knet.traffic.model.body.ContentEncoding
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.ResponseHead
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.message.ProtocolMessageState
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Session-owned adapter from the proxy's streaming capture sink to canonical bounded ingress.
 *
 * The adapter performs only non-blocking event admission, reservation-before-copy allocation, and
 * small state transitions on transport threads. The canonical writer owns all persistence work.
 */
@OptIn(ExperimentalUuidApi::class)
class StreamingProxyCaptureSession internal constructor(
    val sessionId: CaptureSessionId,
    private val ingress: CaptureIngressPort,
    private val limits: CaptureIngressLimits,
) : ProxyCaptureSink {
    private val closed = AtomicBoolean(false)
    private val connections = ConcurrentHashMap.newKeySet<StreamingConnectionCapture>()
    private val shutdownScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun openConnection(metadata: ProxyCaptureConnectionMetadata): ProxyConnectionCapture? {
        if (closed.get()) return null
        val connection = StreamingConnectionCapture(
            sessionId = sessionId,
            connectionId = ConnectionId("proxy-${Uuid.random()}"),
            metadata = metadata,
            ingress = ingress,
            limits = limits,
            onClosed = connections::remove,
        )
        if (!connection.open()) return null
        connections += connection
        if (closed.get()) {
            connection.close(SESSION_CLOSED)
            return null
        }
        return connection
    }

    /** Waits until all capture work admitted before this call is durable. */
    suspend fun flush() {
        ingress.flush()
    }

    /** Terminalizes live connections, drains accepted work, and closes the canonical writer. */
    suspend fun close() {
        if (!closed.compareAndSet(false, true)) return
        connections.toList().forEach { connection -> connection.close(SESSION_CLOSED) }
        ingress.close()
        shutdownScope.cancel()
    }

    /** Starts asynchronous close and waits for bounded process-shutdown cleanup. */
    fun closeAndAwait(timeoutMillis: Long): Boolean {
        require(timeoutMillis > 0L) { "Canonical capture shutdown timeout must be positive." }
        val completed = CountDownLatch(1)
        shutdownScope.launch {
            try {
                close()
            } finally {
                completed.countDown()
            }
        }
        return completed.await(timeoutMillis, TimeUnit.MILLISECONDS)
    }

    private companion object {
        const val SESSION_CLOSED: String = "capture_session_closed"
    }
}

/** One real downstream connection and its monotonically ordered canonical events. */
private class StreamingConnectionCapture(
    private val sessionId: CaptureSessionId,
    private val connectionId: ConnectionId,
    private val metadata: ProxyCaptureConnectionMetadata,
    private val ingress: CaptureIngressPort,
    private val limits: CaptureIngressLimits,
    private val onClosed: (StreamingConnectionCapture) -> Unit,
) : ProxyConnectionCapture {
    private val sequence = AtomicLong(0L)
    private val closed = AtomicBoolean(false)
    private val receivedBodyBytes = AtomicLong(0L)
    private val sentBodyBytes = AtomicLong(0L)
    private val exchanges = ConcurrentHashMap<ExchangeId, StreamingExchangeCapture>()

    fun open(): Boolean = publish(
        CaptureEvent.ConnectionOpened(
            sessionId = sessionId,
            connectionId = connectionId,
            sequence = nextSequence(),
            occurredAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
            ingress = metadata.ingress,
            downstream = metadata.downstream,
            localListener = metadata.localListener,
            transportProtocol = metadata.transportProtocol,
        )
    )

    override fun startExchange(
        exchangeId: ExchangeId,
        request: RequestHead,
        occurredAtEpochMillis: Long,
        origin: TrafficOrigin,
        streamId: StreamId?,
    ): ProxyExchangeCapture? {
        if (closed.get()) return null
        val exchange = StreamingExchangeCapture(
            sessionId = sessionId,
            connectionId = connectionId,
            exchangeId = exchangeId,
            streamId = streamId,
            ingress = ingress,
            limits = limits,
            nextSequence = ::nextSequence,
            addObservedBytes = ::addObservedBytes,
            onTerminated = { exchanges.remove(exchangeId) },
        )
        val admitted = publish(
            CaptureEvent.ExchangeStarted(
                sessionId = sessionId,
                connectionId = connectionId,
                sequence = nextSequence(),
                occurredAtEpochMillis = occurredAtEpochMillis,
                exchangeId = exchangeId,
                exchangeVersion = 0L,
                streamId = streamId,
                request = request,
                origin = origin,
            )
        )
        if (!admitted) return null
        exchanges[exchangeId] = exchange
        return exchange
    }

    override fun close(errorCode: String?) {
        if (!closed.compareAndSet(false, true)) return
        exchanges.values.toList().forEach { exchange -> exchange.cancelForConnectionClose() }
        publish(
            CaptureEvent.ConnectionClosed(
                sessionId = sessionId,
                connectionId = connectionId,
                sequence = nextSequence(),
                occurredAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                receivedBytes = receivedBodyBytes.get(),
                sentBytes = sentBodyBytes.get(),
                errorCode = errorCode,
            )
        )
        exchanges.clear()
        onClosed(this)
    }

    private fun addObservedBytes(direction: TrafficDirection, bytes: Long) {
        when (direction) {
            TrafficDirection.CLIENT_TO_SERVER -> receivedBodyBytes.addAndGet(bytes)
            TrafficDirection.SERVER_TO_CLIENT -> sentBodyBytes.addAndGet(bytes)
        }
    }

    private fun nextSequence(): Long = sequence.incrementAndGet()

    private fun publish(event: CaptureEvent): Boolean = ingress.tryPublish(event) is CapturePublishResult.Accepted
}

/** One exchange with independently bounded request and response body ownership. */
private class StreamingExchangeCapture(
    private val sessionId: CaptureSessionId,
    private val connectionId: ConnectionId,
    override val exchangeId: ExchangeId,
    private val streamId: StreamId?,
    private val ingress: CaptureIngressPort,
    private val limits: CaptureIngressLimits,
    private val nextSequence: () -> Long,
    private val addObservedBytes: (TrafficDirection, Long) -> Unit,
    private val onTerminated: () -> Unit,
) : ProxyExchangeCapture {
    private val lock = Any()
    private val bodies = mutableMapOf<TrafficDirection, StreamingBodyState>()
    private val completedBodies = mutableSetOf<TrafficDirection>()
    private var version: Long = 0L
    private var terminal: Boolean = false

    override fun startMessage(metadata: ProxyMessageCaptureMetadata): ProxyMessageCapture? {
        val admitted = synchronized(lock) {
            if (terminal) return null
            ingress.tryPublish(
                CaptureEvent.ProtocolMessageStarted(
                    sessionId = sessionId,
                    connectionId = connectionId,
                    sequence = nextSequence(),
                    occurredAtEpochMillis = metadata.occurredAtEpochMillis,
                    messageId = metadata.messageId,
                    exchangeId = exchangeId,
                    streamId = metadata.streamId ?: streamId,
                    protocol = metadata.protocol,
                    kind = metadata.kind,
                    direction = metadata.direction,
                    messageSequence = metadata.messageSequence,
                    declaredBytes = metadata.declaredBytes,
                    compressed = metadata.compressed,
                    compressionEncoding = metadata.compressionEncoding,
                )
            ) is CapturePublishResult.Accepted
        }
        if (!admitted) return null
        return StreamingMessageCapture(
            sessionId = sessionId,
            connectionId = connectionId,
            exchangeId = exchangeId,
            metadata = metadata,
            ingress = ingress,
            limits = limits,
            nextSequence = nextSequence,
            publishGap = ::publishGap,
        )
    }

    override fun tryReserveBody(
        direction: TrafficDirection,
        contentEncoding: ContentEncoding?,
        requestedBytes: Int,
    ): ProxyBodyReservation? = synchronized(lock) {
        if (terminal || requestedBytes <= 0) return@synchronized null
        val body = bodies.getOrPut(direction) {
            StreamingBodyState(
                bodyId = BodyId(Uuid.random().toString()),
                exchangeVersion = ++version,
                contentEncoding = contentEncoding,
            )
        }
        if (body.captureStopped) return@synchronized null
        val remaining = (limits.perBodyStoredBytes - body.acceptedBytes).coerceAtLeast(0L)
        if (remaining == 0L) {
            body.captureStopped = true
            return@synchronized null
        }
        val reservedBytes = minOf(
            requestedBytes,
            limits.maximumChunkBytes,
            remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        )
        val reservation = ingress.tryReserveBody(
            connectionId = connectionId,
            exchangeId = exchangeId,
            exchangeVersion = body.exchangeVersion,
            direction = direction,
            bodyId = body.bodyId,
            contentEncoding = body.contentEncoding,
            requestedBytes = reservedBytes,
        )
        if (reservation == null) {
            body.rejectedReservationBytes += reservedBytes
            body.captureStopped = true
            return@synchronized null
        }
        StreamingReservation(reservation, nextSequence) { accepted ->
            synchronized(lock) {
                if (accepted) {
                    body.acceptedBytes += reservedBytes
                } else {
                    body.rejectedReservationBytes += reservedBytes
                    body.captureStopped = true
                }
            }
        }
    }

    override fun completeBody(
        direction: TrafficDirection,
        observedBytes: Long,
        occurredAtEpochMillis: Long,
    ) = finishBody(direction, observedBytes, occurredAtEpochMillis, explicitOutcome = null)

    override fun cancelBody(
        direction: TrafficDirection,
        observedBytes: Long,
        occurredAtEpochMillis: Long,
        errorCode: String,
    ) = finishBody(
        direction = direction,
        observedBytes = observedBytes,
        occurredAtEpochMillis = occurredAtEpochMillis,
        explicitOutcome = BodyCaptureOutcome.Failed(BodyFailure.Custom(errorCode)),
    )

    /** Finalizes one body with either inferred truncation or an explicit transport failure. */
    private fun finishBody(
        direction: TrafficDirection,
        observedBytes: Long,
        occurredAtEpochMillis: Long,
        explicitOutcome: BodyCaptureOutcome?,
    ) {
        require(observedBytes >= 0L) { "Observed body size must not be negative." }
        val body = synchronized(lock) {
            if (terminal) return
            if (!completedBodies.add(direction)) return
            bodies[direction]
        }
        if (observedBytes == 0L) return
        addObservedBytes(direction, observedBytes)
        if (body == null || body.acceptedBytes == 0L) {
            val alreadyCounted = body?.rejectedReservationBytes ?: 0L
            publishGap((observedBytes - alreadyCounted).coerceAtLeast(0L), occurredAtEpochMillis)
            return
        }
        val outcome = explicitOutcome ?: if (body.acceptedBytes == observedBytes) {
            BodyCaptureOutcome.Complete
        } else {
            BodyCaptureOutcome.Truncated(body.acceptedBytes)
        }
        ingress.tryCompleteBody(
            connectionId = connectionId,
            exchangeId = exchangeId,
            exchangeVersion = body.exchangeVersion,
            direction = direction,
            bodyId = body.bodyId,
            observedBytes = observedBytes,
            outcome = outcome,
            sequence = nextSequence(),
            occurredAtEpochMillis = occurredAtEpochMillis,
        )
        val uncountedDroppedBytes =
            (observedBytes - body.acceptedBytes - body.rejectedReservationBytes).coerceAtLeast(0L)
        publishGap(uncountedDroppedBytes, occurredAtEpochMillis)
    }

    override fun observeResponse(response: ResponseHead, occurredAtEpochMillis: Long) {
        val responseVersion = synchronized(lock) {
            if (terminal) return
            ++version
        }
        ingress.tryPublish(
            CaptureEvent.ResponseObserved(
                sessionId = sessionId,
                connectionId = connectionId,
                sequence = nextSequence(),
                occurredAtEpochMillis = occurredAtEpochMillis,
                exchangeId = exchangeId,
                exchangeVersion = responseVersion,
                response = response,
            )
        )
    }

    override fun observeTrailers(
        direction: TrafficDirection,
        trailers: List<HeaderField>,
        occurredAtEpochMillis: Long,
    ) {
        if (trailers.isEmpty()) return
        val trailerVersion = synchronized(lock) {
            if (terminal) return
            ++version
        }
        ingress.tryPublish(
            CaptureEvent.TrailersObserved(
                sessionId = sessionId,
                connectionId = connectionId,
                sequence = nextSequence(),
                occurredAtEpochMillis = occurredAtEpochMillis,
                exchangeId = exchangeId,
                exchangeVersion = trailerVersion,
                direction = direction,
                trailers = trailers,
            )
        )
    }

    override fun terminate(
        state: ExchangeState,
        timings: ExchangeTimings,
        occurredAtEpochMillis: Long,
        errorCode: String?,
    ) {
        val terminalVersion = synchronized(lock) {
            if (terminal) return
            terminal = true
            ++version
        }
        ingress.tryPublish(
            CaptureEvent.ExchangeTerminated(
                sessionId = sessionId,
                connectionId = connectionId,
                sequence = nextSequence(),
                occurredAtEpochMillis = occurredAtEpochMillis,
                exchangeId = exchangeId,
                exchangeVersion = terminalVersion,
                state = state,
                timings = timings,
                errorCode = errorCode,
            )
        )
        onTerminated()
    }

    fun cancelForConnectionClose() {
        terminate(
            state = ExchangeState.CANCELLED,
            timings = ExchangeTimings(),
            occurredAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
            errorCode = CONNECTION_CLOSED,
        )
    }

    private fun publishGap(bytes: Long, occurredAtEpochMillis: Long) {
        if (bytes <= 0L) return
        ingress.tryPublish(
            CaptureEvent.GapObserved(
                sessionId = sessionId,
                connectionId = connectionId,
                sequence = nextSequence(),
                occurredAtEpochMillis = occurredAtEpochMillis,
                droppedEvents = 0L,
                droppedBodyBytes = bytes,
                reasonCode = BODY_CAPTURE_TRUNCATED,
            )
        )
    }

    private data class StreamingBodyState(
        val bodyId: BodyId,
        val exchangeVersion: Long,
        val contentEncoding: ContentEncoding?,
        var acceptedBytes: Long = 0L,
        var rejectedReservationBytes: Long = 0L,
        var captureStopped: Boolean = false,
    )

    private companion object {
        const val CONNECTION_CLOSED: String = "downstream_connection_closed"
        const val BODY_CAPTURE_TRUNCATED: String = "stream_body_capture_truncated"
    }
}

/** One independently bounded framed-message payload owned by a parent exchange. */
private class StreamingMessageCapture(
    private val sessionId: CaptureSessionId,
    private val connectionId: ConnectionId,
    private val exchangeId: ExchangeId,
    private val metadata: ProxyMessageCaptureMetadata,
    private val ingress: CaptureIngressPort,
    private val limits: CaptureIngressLimits,
    private val nextSequence: () -> Long,
    private val publishGap: (Long, Long) -> Unit,
) : ProxyMessageCapture {
    override val messageId: ProtocolMessageId = metadata.messageId
    private val lock = Any()
    private val bodyId = BodyId(Uuid.random().toString())
    private var acceptedBytes: Long = 0L
    private var rejectedReservationBytes: Long = 0L
    private var captureStopped: Boolean = false
    private var terminal: Boolean = false

    override fun tryReservePayload(requestedBytes: Int): ProxyBodyReservation? = synchronized(lock) {
        if (terminal || captureStopped || requestedBytes <= 0) return@synchronized null
        val remaining = (limits.perBodyStoredBytes - acceptedBytes).coerceAtLeast(0L)
        if (remaining == 0L) {
            captureStopped = true
            return@synchronized null
        }
        val reservedBytes = minOf(
            requestedBytes,
            limits.maximumChunkBytes,
            remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        )
        val reservation = ingress.tryReserveMessageBody(
            connectionId = connectionId,
            exchangeId = exchangeId,
            messageId = messageId,
            direction = metadata.direction,
            bodyId = bodyId,
            contentEncoding = metadata.compressionEncoding?.let(ContentEncoding::fromToken),
            requestedBytes = reservedBytes,
        )
        if (reservation == null) {
            rejectedReservationBytes += reservedBytes
            captureStopped = true
            return@synchronized null
        }
        StreamingReservation(reservation, nextSequence) { accepted ->
            synchronized(lock) {
                if (accepted) {
                    acceptedBytes += reservedBytes
                } else {
                    rejectedReservationBytes += reservedBytes
                    captureStopped = true
                }
            }
        }
    }

    override fun complete(observedBytes: Long, occurredAtEpochMillis: Long) = terminate(
        observedBytes = observedBytes,
        state = if (acceptedBytes == observedBytes) {
            ProtocolMessageState.COMPLETE
        } else {
            ProtocolMessageState.TRUNCATED
        },
        occurredAtEpochMillis = occurredAtEpochMillis,
    )

    override fun terminate(
        observedBytes: Long,
        state: ProtocolMessageState,
        occurredAtEpochMillis: Long,
        errorCode: String?,
    ) {
        require(observedBytes >= 0L) { "Observed protocol-message bytes must not be negative." }
        require(state != ProtocolMessageState.IN_PROGRESS) { "Protocol-message termination must be terminal." }
        val captured = synchronized(lock) {
            if (terminal) return
            terminal = true
            acceptedBytes
        }
        if (captured > 0L) {
            val outcome = when (state) {
                ProtocolMessageState.COMPLETE -> BodyCaptureOutcome.Complete
                ProtocolMessageState.TRUNCATED -> BodyCaptureOutcome.Truncated(captured)
                ProtocolMessageState.FAILED, ProtocolMessageState.CANCELLED ->
                    BodyCaptureOutcome.Failed(BodyFailure.Custom(errorCode ?: state.name.lowercase()))
                ProtocolMessageState.IN_PROGRESS -> error("Unreachable in-progress termination.")
            }
            ingress.tryCompleteMessageBody(
                connectionId = connectionId,
                exchangeId = exchangeId,
                messageId = messageId,
                direction = metadata.direction,
                bodyId = bodyId,
                observedBytes = observedBytes,
                outcome = outcome,
                sequence = nextSequence(),
                occurredAtEpochMillis = occurredAtEpochMillis,
            )
        }
        val uncounted = (observedBytes - captured - rejectedReservationBytes).coerceAtLeast(0L)
        publishGap(uncounted, occurredAtEpochMillis)
        ingress.tryPublish(
            CaptureEvent.ProtocolMessageTerminated(
                sessionId = sessionId,
                connectionId = connectionId,
                sequence = nextSequence(),
                occurredAtEpochMillis = occurredAtEpochMillis,
                messageId = messageId,
                observedBytes = observedBytes,
                state = state,
                errorCode = errorCode,
            )
        )
    }
}

/** Transfers one application reservation without exposing the canonical writer to Netty. */
private class StreamingReservation(
    private val delegate: BodyChunkReservation,
    private val nextSequence: () -> Long,
    private val onTerminal: (Boolean) -> Unit,
) : ProxyBodyReservation {
    private val terminal = AtomicBoolean(false)
    override val writableBytes: ByteArray = delegate.writableBytes

    override fun publish(occurredAtEpochMillis: Long): Boolean {
        check(terminal.compareAndSet(false, true)) { "Streaming reservation is already terminal." }
        val accepted = delegate.publish(
            sequence = nextSequence(),
            occurredAtEpochMillis = occurredAtEpochMillis,
            endOfBody = false,
        ) is CapturePublishResult.Accepted
        onTerminal(accepted)
        return accepted
    }

    override fun cancel() {
        if (terminal.compareAndSet(false, true)) {
            delegate.cancel()
            onTerminal(false)
        }
    }
}
