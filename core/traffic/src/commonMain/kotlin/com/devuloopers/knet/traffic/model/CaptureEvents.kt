package com.devuloopers.knet.traffic.model

import com.devuloopers.knet.traffic.id.CaptureSessionId
import com.devuloopers.knet.traffic.id.ConnectionId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.StreamId
import com.devuloopers.knet.traffic.id.ProtocolMessageId
import com.devuloopers.knet.traffic.id.OpaqueFlowId
import com.devuloopers.knet.traffic.model.body.BodyRef
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.ResponseHead
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.message.MessageProtocolId
import com.devuloopers.knet.traffic.model.message.ProtocolMessageKind
import com.devuloopers.knet.traffic.model.message.ProtocolMessageState

/** Direction of body or duplex content relative to the inspected client. */
public enum class TrafficDirection {
    CLIENT_TO_SERVER,
    SERVER_TO_CLIENT,
}

/**
 * Validated network endpoint captured without depending on a JVM socket type.
 *
 * @property host Non-blank host or address.
 * @property port Optional valid transport port.
 */
public data class TrafficEndpoint(
    public val host: String,
    public val port: Int? = null,
) {
    init {
        require(host.isNotBlank()) { "Traffic endpoint host must not be blank." }
        require(port == null || port in 1..65_535) { "Traffic endpoint port must be valid." }
    }
}

/**
 * Immutable metadata event accepted by one session-owned capture ingress.
 *
 * Events never contain transport buffers or arbitrary-size body arrays. [sequence] orders all
 * events for one connection, while exchange events also carry a monotonic exchange version.
 */
public sealed interface CaptureEvent {
    /** Session that owns this event. */
    public val sessionId: CaptureSessionId

    /** Connection whose ordered event stream contains this event. */
    public val connectionId: ConnectionId

    /** Monotonic per-connection sequence assigned at the transport boundary. */
    public val sequence: Long

    /** Wall-clock observation timestamp. */
    public val occurredAtEpochMillis: Long

    /**
     * Records an admitted downstream connection.
     *
     * @property ingress Client reachability and authenticated identity metadata.
     * @property downstream Client endpoint when available.
     * @property localListener Listener endpoint that admitted the connection.
     * @property transportProtocol Stable transport token such as `tcp` or `quic`.
     */
    public data class ConnectionOpened(
        override val sessionId: CaptureSessionId,
        override val connectionId: ConnectionId,
        override val sequence: Long,
        override val occurredAtEpochMillis: Long,
        public val ingress: IngressContext,
        public val downstream: TrafficEndpoint? = null,
        public val localListener: TrafficEndpoint,
        public val transportProtocol: String,
    ) : CaptureEvent {
        init {
            validateEventCoordinates(sequence, occurredAtEpochMillis)
            require(transportProtocol.isNotBlank()) { "Transport protocol must not be blank." }
        }
    }

    /**
     * Starts one HTTP exchange with canonical request metadata.
     *
     * @property exchangeId Stable logical exchange identifier.
     * @property exchangeVersion First monotonic exchange version.
     * @property streamId Optional multiplexed stream identifier.
     * @property request Canonical request metadata without body bytes.
     * @property origin Feature or client that initiated the exchange.
     * @property appliedNetworkCondition Network-condition policy evidence selected before forwarding.
     */
    public data class ExchangeStarted(
        override val sessionId: CaptureSessionId,
        override val connectionId: ConnectionId,
        override val sequence: Long,
        override val occurredAtEpochMillis: Long,
        public val exchangeId: ExchangeId,
        public val exchangeVersion: Long,
        public val streamId: StreamId? = null,
        public val request: RequestHead,
        public val origin: TrafficOrigin = TrafficOrigin.ProxyClient,
        public val appliedNetworkCondition: AppliedNetworkCondition? = null,
    ) : CaptureEvent {
        init {
            validateEventCoordinates(sequence, occurredAtEpochMillis)
            require(exchangeVersion >= 0L) { "Exchange version must not be negative." }
        }
    }

    /**
     * Starts one payload-opaque transport flow without fabricating HTTP semantics.
     *
     * @property flowId Stable flow identity.
     * @property flowVersion Initial monotonic flow version.
     * @property destination Remote transport endpoint.
     * @property serverName Visible TLS server name, when available.
     * @property transport Ordered-stream or datagram transport.
     * @property security Proven security protocol classification.
     * @property sourceApplicationId Verified source identity, when available.
     * @property policyRuleId Stable selected policy-rule evidence.
     * @property appliedNetworkCondition Network-condition policy evidence selected for this flow.
     * @property offeredApplicationProtocols Bounded offered ALPN tokens.
     * @property offeredTlsVersions Bounded offered TLS-version tokens.
     */
    public data class OpaqueFlowStarted(
        override val sessionId: CaptureSessionId,
        override val connectionId: ConnectionId,
        override val sequence: Long,
        override val occurredAtEpochMillis: Long,
        public val flowId: OpaqueFlowId,
        public val flowVersion: Long,
        public val destination: TrafficEndpoint,
        public val serverName: String?,
        public val transport: OpaqueTransportProtocol,
        public val security: OpaqueSecurityProtocol,
        public val sourceApplicationId: String?,
        public val policyRuleId: String?,
        public val policyAction: OpaqueFlowPolicyAction? = null,
        public val policyGroupId: String? = null,
        public val appliedNetworkCondition: AppliedNetworkCondition? = null,
        public val offeredApplicationProtocols: List<String> = emptyList(),
        public val offeredTlsVersions: List<String> = emptyList(),
    ) : CaptureEvent {
        init {
            validateEventCoordinates(sequence, occurredAtEpochMillis)
            require(flowVersion >= 0L) { "Opaque-flow version must not be negative." }
            require(serverName == null || serverName.isNotBlank()) { "Opaque-flow server name must not be blank." }
            require(sourceApplicationId == null || sourceApplicationId.isNotBlank()) {
                "Opaque-flow source application ID must not be blank."
            }
            require(policyRuleId == null || policyRuleId.isNotBlank()) { "Opaque-flow rule ID must not be blank." }
            require(policyGroupId == null || policyGroupId.isNotBlank()) {
                "Opaque-flow policy group ID must not be blank."
            }
            require(offeredApplicationProtocols.size <= 16 && offeredTlsVersions.size <= 16)
            require((offeredApplicationProtocols + offeredTlsVersions).all { it.isNotBlank() && it.length <= 255 })
        }
    }

    /**
     * Terminates one payload-opaque flow with final monotonic counters.
     *
     * @property flowId Stable target flow identity.
     * @property flowVersion Monotonic terminal version.
     * @property uploadedBytes Total client-to-destination bytes.
     * @property downloadedBytes Total destination-to-client bytes.
     * @property outcome Typed terminal outcome.
     */
    public data class OpaqueFlowTerminated(
        override val sessionId: CaptureSessionId,
        override val connectionId: ConnectionId,
        override val sequence: Long,
        override val occurredAtEpochMillis: Long,
        public val flowId: OpaqueFlowId,
        public val flowVersion: Long,
        public val uploadedBytes: Long,
        public val downloadedBytes: Long,
        public val outcome: ExchangeTerminalOutcome,
    ) : CaptureEvent {
        init {
            validateEventCoordinates(sequence, occurredAtEpochMillis)
            require(flowVersion > 0L) { "Terminal opaque-flow version must be positive." }
            require(uploadedBytes >= 0L && downloadedBytes >= 0L) {
                "Opaque-flow counters must not be negative."
            }
        }
    }

    /**
     * Attaches a finalized request or response body reference to an exchange.
     *
     * @property exchangeId Target exchange.
     * @property exchangeVersion Monotonic exchange version.
     * @property direction Request or response direction.
     * @property body Finalized body-store reference.
     */
    public data class BodyCaptured(
        override val sessionId: CaptureSessionId,
        override val connectionId: ConnectionId,
        override val sequence: Long,
        override val occurredAtEpochMillis: Long,
        public val exchangeId: ExchangeId,
        public val exchangeVersion: Long,
        public val direction: TrafficDirection,
        public val body: BodyRef,
    ) : CaptureEvent {
        init {
            validateEventCoordinates(sequence, occurredAtEpochMillis)
            require(exchangeVersion >= 0L) { "Exchange version must not be negative." }
        }
    }

    /**
     * Records canonical response metadata for an existing exchange.
     *
     * @property exchangeId Target exchange.
     * @property exchangeVersion Monotonic exchange version.
     * @property response Canonical response metadata without body bytes.
     */
    public data class ResponseObserved(
        override val sessionId: CaptureSessionId,
        override val connectionId: ConnectionId,
        override val sequence: Long,
        override val occurredAtEpochMillis: Long,
        public val exchangeId: ExchangeId,
        public val exchangeVersion: Long,
        public val response: ResponseHead,
    ) : CaptureEvent {
        init {
            validateEventCoordinates(sequence, occurredAtEpochMillis)
            require(exchangeVersion >= 0L) { "Exchange version must not be negative." }
        }
    }

    /**
     * Records the ordered trailers that terminate one request or response body.
     *
     * HTTP/1 chunked messages and HTTP/2 trailing HEADERS share this semantic event. An empty
     * trailer block is not published; end-of-body remains owned by the body lifecycle.
     */
    public data class TrailersObserved(
        override val sessionId: CaptureSessionId,
        override val connectionId: ConnectionId,
        override val sequence: Long,
        override val occurredAtEpochMillis: Long,
        public val exchangeId: ExchangeId,
        public val exchangeVersion: Long,
        public val direction: TrafficDirection,
        public val trailers: List<HeaderField>,
    ) : CaptureEvent {
        init {
            validateEventCoordinates(sequence, occurredAtEpochMillis)
            require(exchangeVersion >= 0L) { "Exchange version must not be negative." }
            require(trailers.isNotEmpty()) { "A trailer event must contain at least one field." }
        }
    }

    /** Starts one framed child message without changing the parent HTTP exchange lifecycle. */
    public data class ProtocolMessageStarted(
        override val sessionId: CaptureSessionId,
        override val connectionId: ConnectionId,
        override val sequence: Long,
        override val occurredAtEpochMillis: Long,
        public val messageId: ProtocolMessageId,
        public val exchangeId: ExchangeId,
        public val streamId: StreamId?,
        public val protocol: MessageProtocolId,
        public val kind: ProtocolMessageKind,
        public val direction: TrafficDirection,
        public val messageSequence: Long,
        public val declaredBytes: Long?,
        public val compressed: Boolean,
        public val compressionEncoding: String?,
    ) : CaptureEvent {
        init {
            validateEventCoordinates(sequence, occurredAtEpochMillis)
            require(messageSequence >= 0L) { "Protocol message sequence must not be negative." }
            require(declaredBytes == null || declaredBytes >= 0L) { "Declared message bytes must not be negative." }
            require(compressionEncoding == null || compressionEncoding.isNotBlank()) {
                "Compression encoding must not be blank."
            }
        }
    }

    /** Moves one framed child message to a terminal state. */
    public data class ProtocolMessageTerminated(
        override val sessionId: CaptureSessionId,
        override val connectionId: ConnectionId,
        override val sequence: Long,
        override val occurredAtEpochMillis: Long,
        public val messageId: ProtocolMessageId,
        public val observedBytes: Long,
        public val state: ProtocolMessageState,
        public val reason: TrafficTerminationReason? = null,
    ) : CaptureEvent {
        init {
            validateEventCoordinates(sequence, occurredAtEpochMillis)
            require(observedBytes >= 0L) { "Observed message bytes must not be negative." }
            require(state != ProtocolMessageState.IN_PROGRESS) { "Message termination requires a terminal state." }
        }
    }

    /**
     * Moves an exchange to a terminal lifecycle state.
     *
     * @property exchangeId Target exchange.
     * @property exchangeVersion Monotonic terminal version.
     * @property outcome Strongly typed terminal state and reason.
     * @property timings Final observed timings.
     */
    public data class ExchangeTerminated(
        override val sessionId: CaptureSessionId,
        override val connectionId: ConnectionId,
        override val sequence: Long,
        override val occurredAtEpochMillis: Long,
        public val exchangeId: ExchangeId,
        public val exchangeVersion: Long,
        public val outcome: ExchangeTerminalOutcome,
        public val timings: ExchangeTimings = ExchangeTimings(),
    ) : CaptureEvent {
        public val state: ExchangeState get() = outcome.state

        init {
            validateEventCoordinates(sequence, occurredAtEpochMillis)
            require(exchangeVersion >= 0L) { "Exchange version must not be negative." }
        }
    }

    /**
     * Records a compact explicit capture gap after bounded ingress saturation.
     *
     * @property droppedEvents Number of metadata events represented by the gap.
     * @property droppedBodyBytes Number of body bytes intentionally not copied.
     * @property reasonCode Stable safe overload/failure code.
     */
    public data class GapObserved(
        override val sessionId: CaptureSessionId,
        override val connectionId: ConnectionId,
        override val sequence: Long,
        override val occurredAtEpochMillis: Long,
        public val droppedEvents: Long,
        public val droppedBodyBytes: Long,
        public val reasonCode: String,
    ) : CaptureEvent {
        init {
            validateEventCoordinates(sequence, occurredAtEpochMillis)
            require(droppedEvents >= 0L) { "Dropped event count must not be negative." }
            require(droppedBodyBytes >= 0L) { "Dropped body bytes must not be negative." }
            require(droppedEvents > 0L || droppedBodyBytes > 0L) { "A capture gap must record a loss." }
            require(reasonCode.isNotBlank()) { "Capture gap reason must not be blank." }
        }
    }

    /**
     * Records terminal connection state after all preceding exchange events.
     *
     * @property receivedBytes Total downstream bytes received when known.
     * @property sentBytes Total downstream bytes sent when known.
     * @property reason Optional strongly typed terminal reason.
     */
    public data class ConnectionClosed(
        override val sessionId: CaptureSessionId,
        override val connectionId: ConnectionId,
        override val sequence: Long,
        override val occurredAtEpochMillis: Long,
        public val receivedBytes: Long,
        public val sentBytes: Long,
        public val reason: TrafficTerminationReason? = null,
    ) : CaptureEvent {
        init {
            validateEventCoordinates(sequence, occurredAtEpochMillis)
            require(receivedBytes >= 0L) { "Received bytes must not be negative." }
            require(sentBytes >= 0L) { "Sent bytes must not be negative." }
        }
    }
}

/** Applies shared event ordering and timestamp validation. */
private fun validateEventCoordinates(sequence: Long, occurredAtEpochMillis: Long) {
    require(sequence >= 0L) { "Capture event sequence must not be negative." }
    require(occurredAtEpochMillis >= 0L) { "Capture event timestamp must not be negative." }
}
