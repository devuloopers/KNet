package com.devuloopers.knet.engine.proxy.capture

import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.ProtocolMessageId
import com.devuloopers.knet.traffic.id.StreamId
import com.devuloopers.knet.traffic.model.ExchangeTerminalOutcome
import com.devuloopers.knet.traffic.model.AppliedNetworkCondition
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.IngressContext
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficEndpoint
import com.devuloopers.knet.traffic.model.TrafficOrigin
import com.devuloopers.knet.traffic.model.TrafficTerminationReason
import com.devuloopers.knet.traffic.model.body.ContentEncoding
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.ResponseHead
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.message.MessageProtocolId
import com.devuloopers.knet.traffic.model.message.ProtocolMessageKind
import com.devuloopers.knet.traffic.model.message.ProtocolMessageState

/** Metadata supplied when one downstream transport connection is admitted. */
data class ProxyCaptureConnectionMetadata(
    val ingress: IngressContext,
    val downstream: TrafficEndpoint?,
    val localListener: TrafficEndpoint,
    val transportProtocol: String = "tcp",
)

/**
 * Non-blocking side-output boundary used by the proxy transport.
 *
 * Implementations may reserve/copy bounded bytes and enqueue metadata, but must never perform
 * storage, parsing, or other blocking work on the caller's event loop.
 */
fun interface ProxyCaptureSink {
    /** Admits capture for one transport connection, or returns `null` without affecting forwarding. */
    fun openConnection(metadata: ProxyCaptureConnectionMetadata): ProxyConnectionCapture?
}

/** Connection-scoped ordered capture ownership. */
interface ProxyConnectionCapture : AutoCloseable {
    /** Starts one exchange using the transport-assigned stable identifier. */
    fun startExchange(
        exchangeId: ExchangeId,
        request: RequestHead,
        occurredAtEpochMillis: Long,
        origin: TrafficOrigin = TrafficOrigin.ProxyClient,
        streamId: StreamId? = null,
    ): ProxyExchangeCapture?

    /** Starts one exchange and stamps the Network Conditions selection made before forwarding. */
    fun startExchange(
        exchangeId: ExchangeId,
        request: RequestHead,
        occurredAtEpochMillis: Long,
        origin: TrafficOrigin = TrafficOrigin.ProxyClient,
        streamId: StreamId? = null,
        appliedNetworkCondition: AppliedNetworkCondition?,
    ): ProxyExchangeCapture? = startExchange(exchangeId, request, occurredAtEpochMillis, origin, streamId)

    /** Starts one payload-opaque transport flow, or returns `null` while forwarding continues. */
    fun startOpaqueFlow(metadata: ProxyOpaqueFlowCaptureMetadata): ProxyOpaqueFlowCapture? = null

    /** Closes the capture side output without closing the transport. */
    fun close(reason: TrafficTerminationReason?)

    /** Closes normally. */
    override fun close(): Unit = close(reason = null)
}

/** Transport protocol represented by an opaque flow without inventing application semantics. */
enum class ProxyOpaqueTransportProtocol {
    /** Ordered byte-stream transport. */
    TCP,

    /** Datagram transport. */
    UDP,
}

/** Security classification retained for an opaque transport flow. */
enum class ProxyOpaqueSecurityProtocol {
    /** End-to-end TLS carried without interception. */
    TLS,

    /** Payload-opaque transport with no proven security protocol. */
    UNKNOWN,
}

/** Exact protected-traffic action selected before this opaque flow was admitted. */
enum class ProxyOpaquePolicyAction {
    INSPECT,
    TUNNEL,
    BLOCK,
    BYPASS,
}

/**
 * Immutable metadata supplied when a payload-opaque flow is admitted.
 *
 * @property destination Validated remote transport endpoint.
 * @property serverName Visible TLS server name, or `null` when unavailable.
 * @property transport Ordered-stream or datagram transport.
 * @property security Proven security classification without payload inference.
 * @property policyRuleId Stable rule evidence, or `null` for the global default.
 * @property sourceApplicationId Verified source package/signing identity, when available.
 * @property appliedNetworkCondition Network-condition profile/rule evidence selected for this flow.
 * @property offeredApplicationProtocols Bounded ALPN tokens offered by the client.
 * @property offeredTlsVersions Bounded TLS-version tokens offered by the client.
 * @property occurredAtEpochMillis Flow admission time on the Unix epoch.
 */
data class ProxyOpaqueFlowCaptureMetadata(
    val destination: TrafficEndpoint,
    val serverName: String?,
    val transport: ProxyOpaqueTransportProtocol,
    val security: ProxyOpaqueSecurityProtocol,
    val policyRuleId: String?,
    val policyAction: ProxyOpaquePolicyAction? = null,
    val policyGroupId: String? = null,
    val sourceApplicationId: String? = null,
    val appliedNetworkCondition: AppliedNetworkCondition? = null,
    val occurredAtEpochMillis: Long,
    val offeredApplicationProtocols: List<String> = emptyList(),
    val offeredTlsVersions: List<String> = emptyList(),
) {
    init {
        require(policyRuleId == null || policyRuleId.isNotBlank()) { "Opaque-flow rule ID must not be blank." }
        require(policyGroupId == null || policyGroupId.isNotBlank()) { "Opaque-flow policy group ID must not be blank." }
        require(sourceApplicationId == null || sourceApplicationId.isNotBlank()) {
            "Opaque-flow source application ID must not be blank."
        }
        require(occurredAtEpochMillis >= 0L) { "Opaque-flow timestamp must not be negative." }
        require(offeredApplicationProtocols.size <= MAXIMUM_TLS_METADATA_VALUES)
        require(offeredTlsVersions.size <= MAXIMUM_TLS_METADATA_VALUES)
        require((offeredApplicationProtocols + offeredTlsVersions).all { it.isNotBlank() && it.length <= 255 })
    }

    private companion object {
        const val MAXIMUM_TLS_METADATA_VALUES: Int = 16
    }
}

/** Non-blocking counter and lifecycle side output for one payload-opaque flow. */
interface ProxyOpaqueFlowCapture {
    /** Records bytes accepted from one side without copying or retaining their payload. */
    fun observeBytes(
        direction: TrafficDirection,
        byteCount: Int,
        occurredAtEpochMillis: Long,
    )

    /** Publishes exactly one terminal state for the opaque flow. */
    fun terminate(
        outcome: ExchangeTerminalOutcome,
        occurredAtEpochMillis: Long,
    )
}

/** Exchange-scoped canonical capture side output. */
interface ProxyExchangeCapture {
    val exchangeId: ExchangeId

    /** Reserves owned bytes before the transport copies from a reference-counted buffer. */
    fun tryReserveBody(
        direction: TrafficDirection,
        contentEncoding: ContentEncoding?,
        requestedBytes: Int,
    ): ProxyBodyReservation?

    /** Finalizes one direction with its complete observed wire-byte count. */
    fun completeBody(
        direction: TrafficDirection,
        observedBytes: Long,
        occurredAtEpochMillis: Long,
    )

    /** Terminates an incomplete body with a stable transport failure code. */
    fun cancelBody(
        direction: TrafficDirection,
        observedBytes: Long,
        occurredAtEpochMillis: Long,
        reason: TrafficTerminationReason,
    )

    /** Starts one framed child message, or returns null while forwarding remains unaffected. */
    fun startMessage(metadata: ProxyMessageCaptureMetadata): ProxyMessageCapture? = null

    /** Publishes response metadata without body bytes. */
    fun observeResponse(response: ResponseHead, occurredAtEpochMillis: Long)

    /** Publishes ordered request or response trailers without treating them as ordinary headers. */
    fun observeTrailers(
        direction: TrafficDirection,
        trailers: List<HeaderField>,
        occurredAtEpochMillis: Long,
    ) = Unit

    /** Publishes exactly one terminal exchange state. */
    fun terminate(
        outcome: ExchangeTerminalOutcome,
        timings: ExchangeTimings,
        occurredAtEpochMillis: Long,
    )
}

/** Transport-neutral metadata supplied when a framed child message starts. */
data class ProxyMessageCaptureMetadata(
    val messageId: ProtocolMessageId,
    val streamId: StreamId?,
    val protocol: MessageProtocolId,
    val kind: ProtocolMessageKind,
    val direction: TrafficDirection,
    val messageSequence: Long,
    val declaredBytes: Long?,
    val compressed: Boolean,
    val compressionEncoding: String?,
    val occurredAtEpochMillis: Long,
)

/** Message-scoped bounded capture side output independent from any protocol implementation. */
interface ProxyMessageCapture {
    val messageId: ProtocolMessageId

    /** Reserves owned payload bytes before copying from a transport buffer. */
    fun tryReservePayload(requestedBytes: Int): ProxyBodyReservation?

    /** Finalizes a successfully observed framed message. */
    fun complete(observedBytes: Long, occurredAtEpochMillis: Long)

    /** Finalizes a truncated, malformed, failed, or cancelled framed message. */
    fun terminate(
        observedBytes: Long,
        state: ProtocolMessageState,
        occurredAtEpochMillis: Long,
        reason: TrafficTerminationReason? = null,
    )
}

/** Exclusive bounded capture allocation. Ownership transfers exactly once through [publish] or [cancel]. */
interface ProxyBodyReservation {
    val writableBytes: ByteArray

    /** Enqueues the filled allocation and transfers ownership. */
    fun publish(occurredAtEpochMillis: Long): Boolean

    /** Releases an unused allocation. */
    fun cancel()
}
