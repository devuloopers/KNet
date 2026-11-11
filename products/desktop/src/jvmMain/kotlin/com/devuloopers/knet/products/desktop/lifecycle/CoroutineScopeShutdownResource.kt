package com.devuloopers.knet.products.desktop.lifecycle

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Cancels and drains one application work scope before dependent storage closes.
 * Call only from the lifecycle shutdown worker/hook, never the Compose event thread. The future bridges a
 * coroutine completion signal to the existing synchronous shutdown contract on its dedicated worker.
 *
 * @param scope Owns every child operation that must unwind before storage teardown.
 * @param maximumWait Bounds cleanup; timeout/interruption propagates to the lifecycle's failure diagnostics.
 */
internal class CoroutineScopeShutdownResource(
    scope: CoroutineScope,
    private val maximumWait: Duration = 5.seconds,
) : ShutdownAware {
    private val owner = requireNotNull(scope.coroutineContext[Job]) { "Shutdown scope must have an owner job." }

    init { require(maximumWait.isFinite() && maximumWait > Duration.ZERO) }

    /** Waits for cancellation cleanup, including transaction rollback; repeated close remains harmless. */
    override fun close() {
        val completion = CompletableFuture<Unit>()
        val subscription = owner.invokeOnCompletion { completion.complete(Unit) }
        try {
            owner.cancel()
            completion.get(maximumWait.inWholeMilliseconds.coerceAtLeast(1), TimeUnit.MILLISECONDS)
        } catch (failure: InterruptedException) {
            Thread.currentThread().interrupt()
            throw failure
        } finally { subscription.dispose() }
    }
}
