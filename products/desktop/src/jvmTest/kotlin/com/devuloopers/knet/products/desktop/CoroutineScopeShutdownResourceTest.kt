package com.devuloopers.knet.products.desktop

import com.devuloopers.knet.products.desktop.lifecycle.CoroutineScopeShutdownResource
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

/** Shutdown waits for suspended cleanup before storage closes, while a stuck child retains an explicit deadline. */
class CoroutineScopeShutdownResourceTest {
    @Test fun `scope close drains transaction style cancellation cleanup and is idempotent`() = runBlocking {
        val owner = SupervisorJob()
        val scope = CoroutineScope(owner + Dispatchers.Default)
        val started = CompletableDeferred<Unit>()
        val unwound = AtomicBoolean(false)
        scope.launch {
            try { started.complete(Unit); awaitCancellation() }
            finally { withContext(NonCancellable) { delay(50); unwound.set(true) } }
        }
        try {
            started.await()
            val resource = CoroutineScopeShutdownResource(scope)
            resource.close(); resource.close()
            assertTrue(unwound.get())
            assertTrue(owner.isCompleted)
        } finally { owner.cancelAndJoin() }
    }

    @Test fun `a stuck cleanup times out without claiming that its owner has drained`() = runBlocking {
        val owner = SupervisorJob()
        val scope = CoroutineScope(owner + Dispatchers.Default)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        scope.launch {
            try { started.complete(Unit); awaitCancellation() }
            finally { withContext(NonCancellable) { release.await() } }
        }
        try {
            started.await()
            assertFailsWith<TimeoutException> { CoroutineScopeShutdownResource(scope, 20.milliseconds).close() }
            assertFalse(owner.isCompleted)
        } finally { release.complete(Unit); owner.cancelAndJoin() }
    }
}
