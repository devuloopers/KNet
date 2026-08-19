package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.core.logger.LogTags
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Process-scoped owner that terminalizes detached capture generations away from lifecycle callers.
 *
 * The bounded queue prevents rapid pause/resume commands from creating unbounded writer cleanup.
 * One worker preserves retirement ordering and keeps Room writer finalization serialized.
 */
internal class CaptureSessionRetirementOwner {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessions = Channel<StreamingProxyCaptureSession>(capacity = RETIREMENT_CAPACITY)
    private val closed = AtomicBoolean(false)
    private val worker = scope.launch {
        for (session in sessions) {
            runCatching { session.close() }
                .onFailure { failure ->
                    KNetLogger.error(tag = LogTags.PROXY, throwable = failure) {
                        "Failed to retire capture session ${session.sessionId.value}."
                    }
                }
        }
    }

    /** Enqueues a detached generation, applying bounded backpressure only to control commands. */
    suspend fun retire(session: StreamingProxyCaptureSession) {
        check(!closed.get()) { "Capture-session retirement is closed." }
        sessions.send(session)
    }

    /** Drains queued retirement work within the process-shutdown budget. */
    fun closeAndAwait(timeoutMillis: Long): Boolean {
        require(timeoutMillis > 0L) { "Capture retirement timeout must be positive." }
        if (closed.compareAndSet(false, true)) sessions.close()
        val completed = CountDownLatch(1)
        scope.launch {
            try {
                worker.join()
            } finally {
                completed.countDown()
            }
        }
        return completed.await(timeoutMillis, TimeUnit.MILLISECONDS).also {
            scope.cancel()
        }
    }

    private companion object {
        private const val RETIREMENT_CAPACITY: Int = 8
    }
}
