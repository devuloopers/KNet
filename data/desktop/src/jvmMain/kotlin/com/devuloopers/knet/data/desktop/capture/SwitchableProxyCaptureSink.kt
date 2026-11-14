package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.engine.proxy.capture.ProxyCaptureConnectionMetadata
import com.devuloopers.knet.engine.proxy.capture.ProxyCaptureSink
import com.devuloopers.knet.engine.proxy.capture.ProxyConnectionCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyOpaqueFlowCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyOpaqueFlowCaptureMetadata
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.StreamId
import com.devuloopers.knet.traffic.model.TrafficOrigin
import com.devuloopers.knet.traffic.model.AppliedNetworkCondition
import com.devuloopers.knet.traffic.model.TrafficTerminationReason
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.application.contract.traffic.CaptureAdmissionPolicy
import com.devuloopers.knet.domain.policy.PolicyWorkspace
import java.util.concurrent.ConcurrentHashMap

/**
 * Stable proxy capture boundary whose persistence target can change without closing transport channels.
 *
 * Existing transport connections receive a stable connection wrapper. The wrapper opens a connection in
 * the current target lazily after a rotation, so the previous session can be terminalized and deleted while
 * the underlying client socket remains untouched.
 */
internal class SwitchableProxyCaptureSink(
    initialTarget: ProxyCaptureSink,
    initialAdmission: CaptureAdmissionPolicy = CaptureAdmissionPolicy(),
) : ProxyCaptureSink {
    @Volatile
    private var target = Target(initialTarget)
    @Volatile private var admission = initialAdmission
    private val admissionLock = Any()
    private val connections = ConcurrentHashMap.newKeySet<SwitchableProxyConnectionCapture>()

    /**
     * Cuts only newly paused sources at a complete recording boundary. Admission and initial binding
     * share a short lock so a connection cannot register behind the pause sweep. No storage IO or
     * transport closure runs here; closing a capture only enqueues bounded canonical termination work.
     */
    fun setAdmission(policy: CaptureAdmissionPolicy) = synchronized(admissionLock) {
        val previous = admission
        admission = policy
        connections.forEach { connection ->
            if (previous.enabledOn(connection.workspace) && !policy.enabledOn(connection.workspace)) {
                connection.pauseRecording()
            }
        }
    }

    /** Replaces the persistence target used by new connections and subsequent exchanges. */
    fun replaceTarget(replacement: ProxyCaptureSink) {
        target = Target(replacement)
    }

    /** Detaches persistence while keeping the stable transport-facing sink available. */
    fun pause() {
        target = Target(sink = null)
    }

    override fun openConnection(metadata: ProxyCaptureConnectionMetadata): ProxyConnectionCapture =
        synchronized(admissionLock) {
            val connection = SwitchableProxyConnectionCapture(metadata, { target }, { admission }, connections::remove)
            connections += connection
            connection.observeInitialTarget()
            connection
        }

    private class Target(val sink: ProxyCaptureSink?)

    private class SwitchableProxyConnectionCapture(
        private val metadata: ProxyCaptureConnectionMetadata,
        private val currentTarget: () -> Target,
        private val currentAdmission: () -> CaptureAdmissionPolicy,
        private val onClosed: (SwitchableProxyConnectionCapture) -> Unit,
    ) : ProxyConnectionCapture {
        val workspace = PolicyWorkspace.fromIngress(metadata.ingress)
        private val lock = Any()
        private var closed = false
        private var observedTarget: Target? = null
        private var binding: Binding? = null

        override val sessionId: com.devuloopers.knet.traffic.id.CaptureSessionId?
            get() = synchronized(lock) { bindCurrentTarget()?.sessionId }

        fun observeInitialTarget() {
            synchronized(lock) {
                bindCurrentTarget()
            }
        }

        fun pauseRecording() = synchronized(lock) {
            binding?.lease?.close()
            binding?.capture?.close(TrafficTerminationReason.Lifecycle.CAPTURE_PAUSED)
            binding = null
            observedTarget = null
        }

        override fun startExchange(
            exchangeId: ExchangeId,
            request: RequestHead,
            occurredAtEpochMillis: Long,
            origin: TrafficOrigin,
            streamId: StreamId?,
        ): ProxyExchangeCapture? = synchronized(lock) {
            bindCurrentTarget()?.startExchange(exchangeId, request, occurredAtEpochMillis, origin, streamId)
                ?.let { LeasedExchangeCapture(it, checkNotNull(binding).lease) }
        }

        override fun startExchange(
            exchangeId: ExchangeId,
            request: RequestHead,
            occurredAtEpochMillis: Long,
            origin: TrafficOrigin,
            streamId: StreamId?,
            appliedNetworkCondition: AppliedNetworkCondition?,
        ): ProxyExchangeCapture? = synchronized(lock) {
            bindCurrentTarget()?.startExchange(
                exchangeId,
                request,
                occurredAtEpochMillis,
                origin,
                streamId,
                appliedNetworkCondition,
            )?.let { LeasedExchangeCapture(it, checkNotNull(binding).lease) }
        }

        override fun startOpaqueFlow(metadata: ProxyOpaqueFlowCaptureMetadata): ProxyOpaqueFlowCapture? =
            synchronized(lock) {
                bindCurrentTarget()?.startOpaqueFlow(metadata)
            }

        override fun close(reason: TrafficTerminationReason?) {
            synchronized(lock) {
                if (closed) return
                closed = true
                binding?.lease?.close()
                binding?.capture?.close(reason)
                binding = null
                onClosed(this)
            }
        }

        private fun bindCurrentTarget(): ProxyConnectionCapture? {
            if (closed) return null
            if (!currentAdmission().enabledOn(workspace)) return null
            val selected = currentTarget()
            if (observedTarget === selected) return binding?.capture

            binding?.lease?.close()
            binding?.capture?.close(TrafficTerminationReason.Lifecycle.CAPTURE_TARGET_ROTATED)
            binding = null
            observedTarget = selected
            val capture = selected.sink?.openConnection(metadata) ?: return null
            binding = Binding(selected, capture, CaptureRecordingLease())
            return capture
        }

        private data class Binding(
            val target: Target,
            val capture: ProxyConnectionCapture,
            val lease: CaptureRecordingLease,
        )

    }
}
