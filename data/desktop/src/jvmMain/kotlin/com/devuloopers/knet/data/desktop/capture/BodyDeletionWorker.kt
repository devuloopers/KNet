package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.core.logger.LogTags
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.yield
import kotlin.time.Clock

/**
 * One process-owned, bounded durable-outbox consumer. Clear only commits metadata and signals this
 * worker; file removal never owns UI completion. Startup drains crash-left work, failures retry on
 * persisted deadlines, and a conflated notification cannot grow with traffic volume.
 *
 * @property reconciler Bounded file-deletion and database-acknowledgement owner.
 * @property nowEpochMillis Wall clock used for durable due-time comparisons; injectable for tests.
 */
class BodyDeletionWorker(
    private val reconciler: BodyDeletionReconciler,
    private val nowEpochMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val notifications = Channel<Unit>(Channel.CONFLATED)
    private var job: Job? = null

    /** Starts once under the product's shutdown-owned IO scope, returning the same job on repeats. */
    @Synchronized
    fun start(scope: CoroutineScope): Job {
        job?.let { return it }
        return scope.launch {
            coroutineScope {
                val observer = launch {
                    while (isActive) {
                        try {
                            reconciler.observeNextAttemptAt().collect { requestCleanup() }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            delay(5_000)
                        }
                    }
                }
                try {
                    while (isActive) {
                        try {
                            val nextAttempt = reconciler.nextAttemptAt()
                            when {
                                nextAttempt == null -> notifications.receive()
                                nextAttempt > nowEpochMillis() -> waitForWork((nextAttempt - nowEpochMillis()).coerceIn(1L, 60_000L))
                                else -> {
                                    reconciler.reconcile(BATCH_SIZE, nowEpochMillis())
                                    // Bound retained IDs and give live capture writers a chance between batches.
                                    yield()
                                }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            KNetLogger.error(LogTags.TRAFFIC, failure) { "Deferred body cleanup will retry." }
                            delay(5_000)
                        }
                    }
                } finally { observer.cancel() }
            }
        }.also { job = it }
    }

    /** Signals only after the transaction admitting deletion work has committed. */
    fun requestCleanup() { notifications.trySend(Unit) }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private suspend fun waitForWork(delayMillis: Long) {
        select<Unit> {
            notifications.onReceive { }
            onTimeout(delayMillis) { }
        }
    }

    private companion object {
        const val BATCH_SIZE = 256
    }
}
