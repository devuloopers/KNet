package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.application.contract.traffic.BodyChunkReservation
import com.devuloopers.knet.application.contract.traffic.BodyStore
import com.devuloopers.knet.application.contract.traffic.BodyStoreMaintenance
import com.devuloopers.knet.application.contract.traffic.BodyWritePolicy
import com.devuloopers.knet.application.contract.traffic.BodyWriteSession
import com.devuloopers.knet.application.contract.traffic.CaptureIngressHealth
import com.devuloopers.knet.application.contract.traffic.CaptureIngressLimits
import com.devuloopers.knet.application.contract.traffic.CaptureIngress
import com.devuloopers.knet.application.contract.traffic.CapturePublishResult
import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.storage.capture.dao.CanonicalCaptureDao
import com.devuloopers.knet.storage.capture.entity.CaptureGapEntity
import com.devuloopers.knet.storage.capture.entity.CaptureSessionEntity
import com.devuloopers.knet.storage.capture.entity.DeletionOutboxEntity
import com.devuloopers.knet.traffic.id.BodyId
import com.devuloopers.knet.traffic.id.CaptureSessionId
import com.devuloopers.knet.traffic.id.ConnectionId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.ProtocolMessageId
import com.devuloopers.knet.traffic.model.CaptureEvent
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.body.BodyCaptureOutcome
import com.devuloopers.knet.traffic.model.body.BodyFailure
import com.devuloopers.knet.traffic.model.body.ContentEncoding
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Clock

/**
 * One ordered bounded canonical persistence writer for a [sessionId].
 *
 * This is the sole writer for newly captured traffic. Metadata publication never blocks a transport
 * thread, and body arrays are allocated only after a byte-budget reservation succeeds.
 *
 * @property sessionId Capture session exclusively owned by this writer.
 */
class CanonicalSessionWriter private constructor(
    private val sessionId: CaptureSessionId,
    private val dao: CanonicalCaptureDao,
    private val bodyStore: BodyStore,
    private val bodyStoreMaintenance: BodyStoreMaintenance,
    private val limits: CaptureIngressLimits,
) : CaptureIngress {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commands = Channel<WriterCommand>(limits.metadataEventsInFlight)
    private val closeMutex = Mutex()
    private val closed = AtomicBoolean(false)
    private val bodyBytesInFlight = AtomicLong(0L)
    private val droppedMetadataEvents = AtomicLong(0L)
    private val droppedBodyBytes = AtomicLong(0L)
    private val writerFailure = AtomicBoolean(false)
    private val lastGapCoordinates = AtomicReference<GapCoordinates?>(null)
    private val activeReservations = ConcurrentHashMap.newKeySet<BodyChunkReservation>()
    private val _health = MutableStateFlow<CaptureIngressHealth>(CaptureIngressHealth.Healthy)
    override val health: StateFlow<CaptureIngressHealth> = _health.asStateFlow()
    private val activeBodies = mutableMapOf<BodyKey, ActiveBody>()
    private val activeMessageBodies = mutableMapOf<ProtocolMessageId, ActiveBody>()
    private val messageStarts = mutableMapOf<ProtocolMessageId, CaptureEvent.ProtocolMessageStarted>()
    private val writerJob = scope.launch {
        for (command in commands) {
            processCommand(command)
        }
    }

    override fun tryPublish(event: CaptureEvent): CapturePublishResult {
        if (event.sessionId != sessionId) return CapturePublishResult.Rejected(REJECTION_WRONG_SESSION)
        if (closed.get()) return CapturePublishResult.Rejected(REJECTION_CLOSED)
        rememberGapCoordinates(event.connectionId, event.sequence, event.occurredAtEpochMillis)
        val result = commands.trySend(WriterCommand.Metadata(event))
        if (result.isSuccess) return CapturePublishResult.Accepted

        droppedMetadataEvents.incrementAndGet()
        _health.value = CaptureIngressHealth.Degraded(REJECTION_METADATA_SATURATED)
        return CapturePublishResult.Rejected(REJECTION_METADATA_SATURATED)
    }

    override fun tryReserveBody(
        connectionId: ConnectionId,
        exchangeId: ExchangeId,
        exchangeVersion: Long,
        direction: TrafficDirection,
        bodyId: BodyId,
        contentEncoding: ContentEncoding?,
        requestedBytes: Int,
    ): BodyChunkReservation? {
        require(exchangeVersion >= 0L) { "Exchange version must not be negative." }
        require(requestedBytes in 1..limits.maximumChunkBytes) { "Requested body bytes exceed the chunk limit." }
        if (closed.get()) return null
        if (!reserveBodyBytes(requestedBytes)) {
            recordDroppedBodyBytes(connectionId, requestedBytes.toLong())
            return null
        }
        val reservation = Reservation(
            connectionId = connectionId,
            exchangeId = exchangeId,
            exchangeVersion = exchangeVersion,
            direction = direction,
            bodyId = bodyId,
            contentEncoding = contentEncoding,
            bytes = ByteArray(requestedBytes),
        ).also(activeReservations::add)
        if (closed.get()) {
            reservation.cancel()
            return null
        }
        return reservation
    }

    override fun tryCompleteBody(
        connectionId: ConnectionId,
        exchangeId: ExchangeId,
        exchangeVersion: Long,
        direction: TrafficDirection,
        bodyId: BodyId,
        observedBytes: Long,
        outcome: BodyCaptureOutcome,
        sequence: Long,
        occurredAtEpochMillis: Long,
    ): CapturePublishResult {
        require(exchangeVersion >= 0L) { "Exchange version must not be negative." }
        require(observedBytes >= 0L) { "Observed body bytes must not be negative." }
        require(sequence >= 0L) { "Body completion sequence must not be negative." }
        require(occurredAtEpochMillis >= 0L) { "Body completion timestamp must not be negative." }
        if (closed.get()) return CapturePublishResult.Rejected(REJECTION_CLOSED)
        rememberGapCoordinates(connectionId, sequence, occurredAtEpochMillis)
        val result = commands.trySend(
            WriterCommand.BodyCompleted(
                connectionId = connectionId,
                exchangeId = exchangeId,
                exchangeVersion = exchangeVersion,
                direction = direction,
                bodyId = bodyId,
                observedBytes = observedBytes,
                outcome = outcome,
                sequence = sequence,
                occurredAtEpochMillis = occurredAtEpochMillis,
            )
        )
        if (result.isSuccess) return CapturePublishResult.Accepted
        droppedMetadataEvents.incrementAndGet()
        _health.value = CaptureIngressHealth.Degraded(REJECTION_METADATA_SATURATED)
        return CapturePublishResult.Rejected(REJECTION_METADATA_SATURATED)
    }

    override fun tryReserveMessageBody(
        connectionId: ConnectionId,
        exchangeId: ExchangeId,
        messageId: ProtocolMessageId,
        direction: TrafficDirection,
        bodyId: BodyId,
        contentEncoding: ContentEncoding?,
        requestedBytes: Int,
    ): BodyChunkReservation? {
        require(requestedBytes in 1..limits.maximumChunkBytes) { "Requested message bytes exceed the chunk limit." }
        if (closed.get()) return null
        if (!reserveBodyBytes(requestedBytes)) {
            recordDroppedBodyBytes(connectionId, requestedBytes.toLong())
            return null
        }
        val reservation = MessageReservation(
            connectionId = connectionId,
            exchangeId = exchangeId,
            messageId = messageId,
            direction = direction,
            bodyId = bodyId,
            contentEncoding = contentEncoding,
            bytes = ByteArray(requestedBytes),
        ).also(activeReservations::add)
        if (closed.get()) {
            reservation.cancel()
            return null
        }
        return reservation
    }

    override fun tryCompleteMessageBody(
        connectionId: ConnectionId,
        exchangeId: ExchangeId,
        messageId: ProtocolMessageId,
        direction: TrafficDirection,
        bodyId: BodyId,
        observedBytes: Long,
        outcome: BodyCaptureOutcome,
        sequence: Long,
        occurredAtEpochMillis: Long,
    ): CapturePublishResult {
        require(observedBytes >= 0L) { "Observed message bytes must not be negative." }
        require(sequence >= 0L) { "Message body completion sequence must not be negative." }
        require(occurredAtEpochMillis >= 0L) { "Message body completion timestamp must not be negative." }
        if (closed.get()) return CapturePublishResult.Rejected(REJECTION_CLOSED)
        rememberGapCoordinates(connectionId, sequence, occurredAtEpochMillis)
        val result = commands.trySend(
            WriterCommand.MessageBodyCompleted(
                connectionId = connectionId,
                exchangeId = exchangeId,
                messageId = messageId,
                direction = direction,
                bodyId = bodyId,
                observedBytes = observedBytes,
                outcome = outcome,
                sequence = sequence,
                occurredAtEpochMillis = occurredAtEpochMillis,
            )
        )
        if (result.isSuccess) return CapturePublishResult.Accepted
        droppedMetadataEvents.incrementAndGet()
        _health.value = CaptureIngressHealth.Degraded(REJECTION_METADATA_SATURATED)
        return CapturePublishResult.Rejected(REJECTION_METADATA_SATURATED)
    }

    override suspend fun flush() {
        closeMutex.withLock {
            if (closed.get()) return@withLock
            val completion = CompletableDeferred<Unit>()
            commands.send(WriterCommand.Barrier(completion))
            completion.await()
            if (
                !writerFailure.get() &&
                droppedMetadataEvents.get() == 0L &&
                droppedBodyBytes.get() == 0L
            ) {
                _health.value = CaptureIngressHealth.Healthy
            }
        }
    }

    override suspend fun close() {
        closeMutex.withLock {
            if (!closed.compareAndSet(false, true)) return
            activeReservations.toList().forEach { reservation -> reservation.cancel() }
            val completion = CompletableDeferred<Unit>()
            commands.send(WriterCommand.Barrier(completion))
            completion.await()
            commands.close()
            joinAll(writerJob)
            activeBodies.values.forEach { active ->
                runCatching {
                    active.writer.abort(BodyCaptureOutcome.Failed(BodyFailure.SourceFailed))
                }
            }
            activeBodies.clear()
            activeMessageBodies.values.forEach { active ->
                runCatching { active.writer.abort(BodyCaptureOutcome.Failed(BodyFailure.SourceFailed)) }
            }
            activeMessageBodies.clear()
            messageStarts.clear()
            dao.closeSession(
                sessionId = sessionId.value,
                endedAt = Clock.System.now().toEpochMilliseconds(),
                state = SESSION_STATE_CLOSED,
                version = 1L,
            )
            _health.value = CaptureIngressHealth.Closed
            scope.cancel()
        }
    }

    /** Applies one actor command and isolates failures so later terminal events still drain. */
    private suspend fun processCommand(command: WriterCommand) {
        try {
            when (command) {
                is WriterCommand.Metadata -> {
                    persistPendingGap()
                    persistEvent(command.event)
                }
                is WriterCommand.BodyChunk -> {
                    persistPendingGap()
                    persistBodyChunk(command)
                }
                is WriterCommand.BodyCompleted -> {
                    persistPendingGap()
                    persistBodyCompletion(command)
                }
                is WriterCommand.MessageBodyChunk -> {
                    persistPendingGap()
                    persistMessageBodyChunk(command)
                }
                is WriterCommand.MessageBodyCompleted -> {
                    persistPendingGap()
                    persistMessageBodyCompletion(command)
                }
                is WriterCommand.Barrier -> {
                    persistPendingGap()
                    command.completion.complete(Unit)
                }
            }
        } catch (failure: Throwable) {
            writerFailure.set(true)
            _health.value = CaptureIngressHealth.Degraded(WRITER_FAILURE)
            val droppedBodyCommand = when (command) {
                is WriterCommand.BodyChunk -> DroppedBodyCommand(
                    connectionId = command.connectionId,
                    sequence = command.sequence,
                    occurredAtEpochMillis = command.occurredAtEpochMillis,
                    bytes = command.bytes.size,
                )
                is WriterCommand.MessageBodyChunk -> DroppedBodyCommand(
                    connectionId = command.connectionId,
                    sequence = command.sequence,
                    occurredAtEpochMillis = command.occurredAtEpochMillis,
                    bytes = command.bytes.size,
                )
                else -> null
            }
            if (droppedBodyCommand != null) {
                rememberGapCoordinates(
                    droppedBodyCommand.connectionId,
                    droppedBodyCommand.sequence,
                    droppedBodyCommand.occurredAtEpochMillis,
                )
                droppedBodyBytes.addAndGet(droppedBodyCommand.bytes.toLong())
            }
            if (command is WriterCommand.Barrier) command.completion.completeExceptionally(failure)
            KNetLogger.error(TAG, failure) { "Canonical session writer command failed." }
        } finally {
            when (command) {
                is WriterCommand.BodyChunk -> releaseBodyBytes(command.bytes.size)
                is WriterCommand.MessageBodyChunk -> releaseBodyBytes(command.bytes.size)
                else -> Unit
            }
        }
    }

    private data class DroppedBodyCommand(
        val connectionId: ConnectionId,
        val sequence: Long,
        val occurredAtEpochMillis: Long,
        val bytes: Int,
    )

    /** Applies one monotonic metadata event through conditional DAO transitions. */
    private suspend fun persistEvent(event: CaptureEvent) {
        when (event) {
            is CaptureEvent.ConnectionOpened -> dao.insertConnection(CanonicalCaptureEntityMapper.connection(event))
            is CaptureEvent.ExchangeStarted -> dao.insertExchange(CanonicalCaptureEntityMapper.exchange(event))
            is CaptureEvent.ResponseObserved -> dao.updateResponse(
                exchangeId = event.exchangeId.value,
                version = event.exchangeVersion,
                protocol = event.response.protocol.token,
                statusCode = event.response.status.code,
                reasonPhrase = event.response.reasonPhrase,
                headersEncoded = CanonicalCaptureEntityMapper.encodeHeaders(event.response.headers),
            )
            is CaptureEvent.TrailersObserved -> {
                val encoded = CanonicalCaptureEntityMapper.encodeHeaders(event.trailers)
                when (event.direction) {
                    TrafficDirection.CLIENT_TO_SERVER -> dao.updateRequestTrailers(
                        event.exchangeId.value,
                        event.exchangeVersion,
                        encoded,
                    )
                    TrafficDirection.SERVER_TO_CLIENT -> dao.updateResponseTrailers(
                        event.exchangeId.value,
                        event.exchangeVersion,
                        encoded,
                    )
                }
            }
            is CaptureEvent.ProtocolMessageStarted -> {
                dao.insertDuplexMessage(CanonicalCaptureEntityMapper.message(event))
                messageStarts[event.messageId] = event
            }
            is CaptureEvent.ProtocolMessageTerminated -> {
                dao.terminateDuplexMessage(
                    messageId = event.messageId.value,
                    observedBytes = event.observedBytes,
                    state = event.state.name,
                    errorCode = event.reason?.code?.value,
                )
                messageStarts.remove(event.messageId)
            }
            is CaptureEvent.BodyCaptured -> persistBody(event)
            is CaptureEvent.ExchangeTerminated -> dao.terminateExchange(
                exchangeId = event.exchangeId.value,
                version = event.exchangeVersion,
                state = event.state.name,
                completedAt = event.occurredAtEpochMillis,
                dnsMillis = event.timings.dnsMillis,
                connectMillis = event.timings.connectMillis,
                tlsMillis = event.timings.tlsMillis,
                firstByteMillis = event.timings.firstByteMillis,
                downloadMillis = event.timings.downloadMillis,
                totalMillis = event.timings.totalMillis,
                errorCode = event.outcome.reason?.code?.value,
            )
            is CaptureEvent.GapObserved -> dao.insertGap(
                CaptureGapEntity(
                    sessionId = event.sessionId.value,
                    connectionId = event.connectionId.value,
                    sequence = event.sequence,
                    occurredAtEpochMillis = event.occurredAtEpochMillis,
                    droppedEvents = event.droppedEvents,
                    droppedBodyBytes = event.droppedBodyBytes,
                    reasonCode = event.reasonCode,
                )
            )
            is CaptureEvent.ConnectionClosed -> dao.closeConnection(
                connectionId = event.connectionId.value,
                sequence = event.sequence,
                closedAt = event.occurredAtEpochMillis,
                receivedBytes = event.receivedBytes,
                sentBytes = event.sentBytes,
                errorCode = event.reason?.code?.value,
            )
        }
    }

    /** Persists body metadata and converges the file when the lifecycle transition is stale. */
    private suspend fun persistBody(event: CaptureEvent.BodyCaptured) {
        val entity = CanonicalCaptureEntityMapper.body(
            event = event,
            storageKey = bodyStoreMaintenance.storageKey(event.body.id),
        )
        val attached = try {
            when (event.direction) {
                TrafficDirection.CLIENT_TO_SERVER -> dao.attachRequestBody(
                    event.exchangeId.value,
                    event.exchangeVersion,
                    entity,
                )
                TrafficDirection.SERVER_TO_CLIENT -> dao.attachResponseBody(
                    event.exchangeId.value,
                    event.exchangeVersion,
                    entity,
                )
            }
        } catch (failure: Throwable) {
            dao.enqueueDeletion(
                DeletionOutboxEntity(
                    sessionId = sessionId.value,
                    bodyId = event.body.id.value,
                    operation = DELETION_OPERATION_BODY,
                    createdAtEpochMillis = event.occurredAtEpochMillis,
                    attemptCount = 0,
                    lastErrorCode = WRITER_FAILURE,
                )
            )
            throw failure
        }
        if (!attached) bodyStore.delete(event.body.id)
    }

    /** Streams one reserved chunk into the body store and emits body metadata at end-of-body. */
    private suspend fun persistBodyChunk(command: WriterCommand.BodyChunk) {
        val key = BodyKey(command.exchangeId, command.direction)
        val active = activeBodies[key] ?: ActiveBody(
            bodyId = command.bodyId,
            writer = bodyStore.openWrite(
                bodyId = command.bodyId,
                policy = BodyWritePolicy(
                    maximumStoredBytes = limits.perBodyStoredBytes,
                    maximumChunkBytes = limits.maximumChunkBytes,
                ),
                contentEncoding = command.contentEncoding,
            ),
        ).also { activeBodies[key] = it }
        check(active.bodyId == command.bodyId) { "An exchange direction cannot change body identifiers." }
        active.writer.append(command.bytes)
        if (command.endOfBody) {
            val finalized = active.writer.complete().body
            activeBodies.remove(key)
            persistEvent(
                CaptureEvent.BodyCaptured(
                    sessionId = sessionId,
                    connectionId = command.connectionId,
                    sequence = command.sequence,
                    occurredAtEpochMillis = command.occurredAtEpochMillis,
                    exchangeId = command.exchangeId,
                    exchangeVersion = command.exchangeVersion,
                    direction = command.direction,
                    body = finalized,
                )
            )
        }
    }

    /** Finalizes a streaming body with the transport's total observed size and outcome. */
    private suspend fun persistBodyCompletion(command: WriterCommand.BodyCompleted) {
        val key = BodyKey(command.exchangeId, command.direction)
        val active = activeBodies.remove(key) ?: return
        check(active.bodyId == command.bodyId) { "An exchange direction cannot change body identifiers." }
        val stored = active.writer.complete().body
        persistEvent(
            CaptureEvent.BodyCaptured(
                sessionId = sessionId,
                connectionId = command.connectionId,
                sequence = command.sequence,
                occurredAtEpochMillis = command.occurredAtEpochMillis,
                exchangeId = command.exchangeId,
                exchangeVersion = command.exchangeVersion,
                direction = command.direction,
                body = stored.copy(
                    observedBytes = command.observedBytes.coerceAtLeast(stored.storedBytes),
                    outcome = command.outcome,
                ),
            )
        )
    }

    /** Streams one framed-message payload chunk into the common bounded body store. */
    private suspend fun persistMessageBodyChunk(command: WriterCommand.MessageBodyChunk) {
        val active = activeMessageBodies[command.messageId] ?: ActiveBody(
            bodyId = command.bodyId,
            writer = bodyStore.openWrite(
                bodyId = command.bodyId,
                policy = BodyWritePolicy(
                    maximumStoredBytes = limits.perBodyStoredBytes,
                    maximumChunkBytes = limits.maximumChunkBytes,
                ),
                contentEncoding = command.contentEncoding,
            ),
        ).also { activeMessageBodies[command.messageId] = it }
        check(active.bodyId == command.bodyId) { "A protocol message cannot change body identifiers." }
        active.writer.append(command.bytes)
    }

    /** Finalizes one framed-message payload and atomically attaches its body metadata. */
    private suspend fun persistMessageBodyCompletion(command: WriterCommand.MessageBodyCompleted) {
        val active = activeMessageBodies.remove(command.messageId) ?: return
        check(active.bodyId == command.bodyId) { "A protocol message cannot change body identifiers." }
        val start = checkNotNull(messageStarts[command.messageId]) { "Protocol message start metadata is missing." }
        val stored = active.writer.complete().body.copy(
            observedBytes = command.observedBytes,
            outcome = command.outcome,
        )
        val entity = CanonicalCaptureEntityMapper.messageBody(
            event = start,
            body = stored,
            occurredAtEpochMillis = command.occurredAtEpochMillis,
            storageKey = bodyStoreMaintenance.storageKey(stored.id),
        )
        val attached = try {
            dao.attachDuplexMessageBody(command.messageId.value, entity)
        } catch (failure: Throwable) {
            dao.enqueueDeletion(
                DeletionOutboxEntity(
                    sessionId = sessionId.value,
                    bodyId = stored.id.value,
                    operation = DELETION_OPERATION_BODY,
                    createdAtEpochMillis = command.occurredAtEpochMillis,
                    attemptCount = 0,
                    lastErrorCode = WRITER_FAILURE,
                )
            )
            throw failure
        }
        if (!attached) bodyStore.delete(stored.id)
    }

    /** Coalesces saturation counters into a compact durable gap before the next accepted command. */
    private suspend fun persistPendingGap() {
        val droppedEvents = droppedMetadataEvents.getAndSet(0L)
        val droppedBytes = droppedBodyBytes.getAndSet(0L)
        if (droppedEvents == 0L && droppedBytes == 0L) return
        val coordinates = lastGapCoordinates.get() ?: run {
            droppedMetadataEvents.addAndGet(droppedEvents)
            droppedBodyBytes.addAndGet(droppedBytes)
            return
        }
        try {
            dao.insertGap(
                CaptureGapEntity(
                    sessionId = sessionId.value,
                    connectionId = coordinates.connectionId.value,
                    sequence = coordinates.sequence,
                    occurredAtEpochMillis = coordinates.occurredAtEpochMillis,
                    droppedEvents = droppedEvents,
                    droppedBodyBytes = droppedBytes,
                    reasonCode = SATURATION_GAP,
                )
            )
        } catch (failure: Throwable) {
            droppedMetadataEvents.addAndGet(droppedEvents)
            droppedBodyBytes.addAndGet(droppedBytes)
            throw failure
        }
    }

    /** Atomically reserves capture bytes before allocating the corresponding array. */
    private fun reserveBodyBytes(requestedBytes: Int): Boolean {
        while (true) {
            val current = bodyBytesInFlight.get()
            val updated = current + requestedBytes
            if (updated > limits.bodyBytesInFlight) return false
            if (bodyBytesInFlight.compareAndSet(current, updated)) return true
        }
    }

    /** Releases bytes after the writer consumes or rejects their command. */
    private fun releaseBodyBytes(bytes: Int) {
        bodyBytesInFlight.addAndGet(-bytes.toLong())
    }

    /** Records body loss and a coordinate for its compact durable gap. */
    private fun recordDroppedBodyBytes(connectionId: ConnectionId, bytes: Long) {
        droppedBodyBytes.addAndGet(bytes)
        lastGapCoordinates.updateAndGet { current ->
            current ?: GapCoordinates(connectionId, 0L, Clock.System.now().toEpochMilliseconds())
        }
        _health.value = CaptureIngressHealth.Degraded(REJECTION_BODY_SATURATED)
    }

    /** Remembers the newest coordinate available for coalesced saturation gaps. */
    private fun rememberGapCoordinates(connectionId: ConnectionId, sequence: Long, timestamp: Long) {
        lastGapCoordinates.set(GapCoordinates(connectionId, sequence, timestamp))
    }

    /** Reservation implementation with exactly-one publish/cancel ownership transfer. */
    private inner class Reservation(
        private val connectionId: ConnectionId,
        private val exchangeId: ExchangeId,
        private val exchangeVersion: Long,
        private val direction: TrafficDirection,
        private val bodyId: BodyId,
        private val contentEncoding: ContentEncoding?,
        bytes: ByteArray,
    ) : BodyChunkReservation {
        private val terminal = AtomicBoolean(false)
        override val writableBytes: ByteArray = bytes

        override fun publish(
            sequence: Long,
            occurredAtEpochMillis: Long,
            endOfBody: Boolean,
        ): CapturePublishResult {
            require(sequence >= 0L) { "Body chunk sequence must not be negative." }
            require(occurredAtEpochMillis >= 0L) { "Body chunk timestamp must not be negative." }
            check(terminal.compareAndSet(false, true)) { "Body reservation is already terminal." }
            activeReservations.remove(this)
            rememberGapCoordinates(connectionId, sequence, occurredAtEpochMillis)
            val result = commands.trySend(
                WriterCommand.BodyChunk(
                    connectionId = connectionId,
                    exchangeId = exchangeId,
                    exchangeVersion = exchangeVersion,
                    direction = direction,
                    bodyId = bodyId,
                    contentEncoding = contentEncoding,
                    bytes = writableBytes,
                    sequence = sequence,
                    occurredAtEpochMillis = occurredAtEpochMillis,
                    endOfBody = endOfBody,
                )
            )
            if (result.isSuccess) return CapturePublishResult.Accepted
            releaseBodyBytes(writableBytes.size)
            recordDroppedBodyBytes(connectionId, writableBytes.size.toLong())
            return CapturePublishResult.Rejected(REJECTION_METADATA_SATURATED)
        }

        override fun cancel() {
            if (terminal.compareAndSet(false, true)) {
                activeReservations.remove(this)
                releaseBodyBytes(writableBytes.size)
            }
        }
    }

    /** Reservation implementation for a framed child-message payload. */
    private inner class MessageReservation(
        private val connectionId: ConnectionId,
        private val exchangeId: ExchangeId,
        private val messageId: ProtocolMessageId,
        private val direction: TrafficDirection,
        private val bodyId: BodyId,
        private val contentEncoding: ContentEncoding?,
        bytes: ByteArray,
    ) : BodyChunkReservation {
        private val terminal = AtomicBoolean(false)
        override val writableBytes: ByteArray = bytes

        override fun publish(
            sequence: Long,
            occurredAtEpochMillis: Long,
            endOfBody: Boolean,
        ): CapturePublishResult {
            require(sequence >= 0L) { "Message chunk sequence must not be negative." }
            require(occurredAtEpochMillis >= 0L) { "Message chunk timestamp must not be negative." }
            check(terminal.compareAndSet(false, true)) { "Message reservation is already terminal." }
            activeReservations.remove(this)
            rememberGapCoordinates(connectionId, sequence, occurredAtEpochMillis)
            val result = commands.trySend(
                WriterCommand.MessageBodyChunk(
                    connectionId = connectionId,
                    exchangeId = exchangeId,
                    messageId = messageId,
                    direction = direction,
                    bodyId = bodyId,
                    contentEncoding = contentEncoding,
                    bytes = writableBytes,
                    sequence = sequence,
                    occurredAtEpochMillis = occurredAtEpochMillis,
                )
            )
            if (result.isSuccess) return CapturePublishResult.Accepted
            releaseBodyBytes(writableBytes.size)
            recordDroppedBodyBytes(connectionId, writableBytes.size.toLong())
            return CapturePublishResult.Rejected(REJECTION_METADATA_SATURATED)
        }

        override fun cancel() {
            if (terminal.compareAndSet(false, true)) {
                activeReservations.remove(this)
                releaseBodyBytes(writableBytes.size)
            }
        }
    }

    /** Commands serialized by the writer actor. */
    private sealed interface WriterCommand {
        data class Metadata(val event: CaptureEvent) : WriterCommand
        data class BodyChunk(
            val connectionId: ConnectionId,
            val exchangeId: ExchangeId,
            val exchangeVersion: Long,
            val direction: TrafficDirection,
            val bodyId: BodyId,
            val contentEncoding: ContentEncoding?,
            val bytes: ByteArray,
            val sequence: Long,
            val occurredAtEpochMillis: Long,
            val endOfBody: Boolean,
        ) : WriterCommand
        data class BodyCompleted(
            val connectionId: ConnectionId,
            val exchangeId: ExchangeId,
            val exchangeVersion: Long,
            val direction: TrafficDirection,
            val bodyId: BodyId,
            val observedBytes: Long,
            val outcome: BodyCaptureOutcome,
            val sequence: Long,
            val occurredAtEpochMillis: Long,
        ) : WriterCommand
        data class MessageBodyChunk(
            val connectionId: ConnectionId,
            val exchangeId: ExchangeId,
            val messageId: ProtocolMessageId,
            val direction: TrafficDirection,
            val bodyId: BodyId,
            val contentEncoding: ContentEncoding?,
            val bytes: ByteArray,
            val sequence: Long,
            val occurredAtEpochMillis: Long,
        ) : WriterCommand
        data class MessageBodyCompleted(
            val connectionId: ConnectionId,
            val exchangeId: ExchangeId,
            val messageId: ProtocolMessageId,
            val direction: TrafficDirection,
            val bodyId: BodyId,
            val observedBytes: Long,
            val outcome: BodyCaptureOutcome,
            val sequence: Long,
            val occurredAtEpochMillis: Long,
        ) : WriterCommand
        data class Barrier(val completion: CompletableDeferred<Unit>) : WriterCommand
    }

    /** Active body writer keyed by exchange and direction. */
    private data class ActiveBody(val bodyId: BodyId, val writer: BodyWriteSession)

    /** Direction-scoped body key. */
    private data class BodyKey(val exchangeId: ExchangeId, val direction: TrafficDirection)

    /** Coordinates used for compact saturation gaps. */
    private data class GapCoordinates(
        val connectionId: ConnectionId,
        val sequence: Long,
        val occurredAtEpochMillis: Long,
    )

    companion object {
        /**
         * Creates and durably initializes one session writer.
         *
         * @param sessionId New bounded capture session identifier.
         * @param startedAtEpochMillis Session wall-clock start time.
         * @param dao Canonical Room persistence adapter.
         * @param bodyStore Atomic bounded body store.
         * @param bodyStoreMaintenance Opaque finalized-object maintenance boundary.
         * @param limits Count and byte limits enforced before capture allocation.
         * @return Initialized writer safe for non-blocking publication.
         */
        suspend fun open(
            sessionId: CaptureSessionId,
            startedAtEpochMillis: Long,
            dao: CanonicalCaptureDao,
            bodyStore: BodyStore,
            bodyStoreMaintenance: BodyStoreMaintenance,
            limits: CaptureIngressLimits,
        ): CanonicalSessionWriter {
            require(startedAtEpochMillis >= 0L) { "Session start timestamp must not be negative." }
            val inserted = dao.insertSession(
                CaptureSessionEntity(
                    id = sessionId.value,
                    startedAtEpochMillis = startedAtEpochMillis,
                    endedAtEpochMillis = null,
                    state = SESSION_STATE_ACTIVE,
                    version = 0L,
                )
            )
            check(inserted != -1L) { "Capture session ${sessionId.value} already exists." }
            return CanonicalSessionWriter(sessionId, dao, bodyStore, bodyStoreMaintenance, limits)
        }

        private const val TAG = "CanonicalSessionWriter"
        private const val SESSION_STATE_ACTIVE = "ACTIVE"
        private const val SESSION_STATE_CLOSED = "CLOSED"
        private const val DELETION_OPERATION_BODY = "DELETE_BODY"
        private const val REJECTION_WRONG_SESSION = "capture-wrong-session"
        private const val REJECTION_CLOSED = "capture-closed"
        private const val REJECTION_METADATA_SATURATED = "capture-metadata-saturated"
        private const val REJECTION_BODY_SATURATED = "capture-body-saturated"
        private const val SATURATION_GAP = "capture-ingress-saturated"
        private const val WRITER_FAILURE = "canonical-writer-failed"
    }
}
