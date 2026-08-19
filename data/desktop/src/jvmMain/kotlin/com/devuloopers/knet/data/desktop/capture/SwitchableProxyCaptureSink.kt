package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.engine.proxy.capture.ProxyCaptureConnectionMetadata
import com.devuloopers.knet.engine.proxy.capture.ProxyCaptureSink
import com.devuloopers.knet.engine.proxy.capture.ProxyConnectionCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.http.RequestHead

/**
 * Stable proxy capture boundary whose persistence target can change without closing transport channels.
 *
 * Existing transport connections receive a stable connection wrapper. The wrapper opens a connection in
 * the current target lazily after a rotation, so the previous session can be terminalized and deleted while
 * the underlying client socket remains untouched.
 */
internal class SwitchableProxyCaptureSink(initialTarget: ProxyCaptureSink) : ProxyCaptureSink {
    @Volatile
    private var target = Target(initialTarget)

    /** Replaces the persistence target used by new connections and subsequent exchanges. */
    fun replaceTarget(replacement: ProxyCaptureSink) {
        target = Target(replacement)
    }

    /** Detaches persistence while keeping the stable transport-facing sink available. */
    fun pause() {
        target = Target(sink = null)
    }

    override fun openConnection(metadata: ProxyCaptureConnectionMetadata): ProxyConnectionCapture? {
        val connection = SwitchableProxyConnectionCapture(metadata) { target }
        connection.observeInitialTarget()
        return connection
    }

    private class Target(val sink: ProxyCaptureSink?)

    private class SwitchableProxyConnectionCapture(
        private val metadata: ProxyCaptureConnectionMetadata,
        private val currentTarget: () -> Target,
    ) : ProxyConnectionCapture {
        private val lock = Any()
        private var closed = false
        private var observedTarget: Target? = null
        private var binding: Binding? = null

        fun observeInitialTarget() {
            synchronized(lock) {
                bindCurrentTarget()
            }
        }

        override fun startExchange(
            exchangeId: ExchangeId,
            request: RequestHead,
            occurredAtEpochMillis: Long,
        ): ProxyExchangeCapture? = synchronized(lock) {
            bindCurrentTarget()?.startExchange(exchangeId, request, occurredAtEpochMillis)
        }

        override fun close(errorCode: String?) {
            synchronized(lock) {
                if (closed) return
                closed = true
                binding?.capture?.close(errorCode)
                binding = null
            }
        }

        private fun bindCurrentTarget(): ProxyConnectionCapture? {
            if (closed) return null
            val selected = currentTarget()
            if (observedTarget === selected) return binding?.capture

            binding?.capture?.close(CAPTURE_TARGET_ROTATED)
            binding = null
            observedTarget = selected
            val capture = selected.sink?.openConnection(metadata) ?: return null
            binding = Binding(selected, capture)
            return capture
        }

        private data class Binding(
            val target: Target,
            val capture: ProxyConnectionCapture,
        )

        private companion object {
            const val CAPTURE_TARGET_ROTATED: String = "capture_target_rotated"
        }
    }
}
