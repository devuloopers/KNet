package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.core.logger.LogTags
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
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
    private val commands = Channel<RetirementCommand>(capacity = RETIREMENT_CAPACITY)
    private val closed = AtomicBoolean(false)
    private val worker = scope.launch {
        for (command in commands) {
            when (command) {
                is RetirementCommand.Retire -> {
                    val session = command.session
                    runCatching { session.close() }
                        .onFailure { failure ->
                            KNetLogger.error(tag = LogTags.PROXY, throwable = failure) {
                                "Failed to retire capture session ${session.sessionId.value}."
                            }
                        }
                }

                is RetirementCommand.Barrier -> {
                    command.completion.complete(Unit)
                }
            }
        }
    }

    /** Enqueues a detached generation, applying bounded backpressure only to control commands. */
    suspend fun retire(session: StreamingProxyCaptureSession) {
        check(!closed.get()) { "Capture-session retirement is closed." }
        commands.send(RetirementCommand.Retire(session))
    }

    /** Waits until every retirement admitted before this call has fully terminalized its writer. */
    suspend fun awaitDrained() {
        check(!closed.get()) { "Capture-session retirement is closed." }
        val completion = CompletableDeferred<Unit>()
        commands.send(RetirementCommand.Barrier(completion))
        completion.await()
    }

    /** Drains queued retirement work within the process-shutdown budget. */
    fun closeAndAwait(timeoutMillis: Long): Boolean {
        require(timeoutMillis > 0L) { "Capture retirement timeout must be positive." }
        if (closed.compareAndSet(false, true)) commands.close()
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

    private sealed interface RetirementCommand {
        data class Retire(val session: StreamingProxyCaptureSession) : RetirementCommand
        data class Barrier(val completion: CompletableDeferred<Unit>) : RetirementCommand
    }
}
