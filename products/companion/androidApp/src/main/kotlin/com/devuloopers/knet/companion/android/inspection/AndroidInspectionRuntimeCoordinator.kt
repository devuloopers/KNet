package com.devuloopers.knet.companion.android.inspection

import com.devuloopers.knet.companion.application.contract.CompanionInspectionConfiguration
import com.devuloopers.knet.companion.connectivity.inspection.AndroidInspectionBackendResult
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Process-scoped handoff between the KMP inspection controller and Android's system-owned VPN service. */
internal class AndroidInspectionRuntimeCoordinator(
    private val operationTimeoutMillis: Long = DEFAULT_OPERATION_TIMEOUT_MILLIS,
) {
    init {
        require(operationTimeoutMillis > 0L) { "Inspection operation timeout must be positive." }
    }

    private val requestLock = Mutex()
    private val pendingStart = AtomicReference<StartRequest?>(null)
    private val pendingStop = AtomicReference<CompletableDeferred<Unit>?>(null)

    @Volatile
    private var running: Boolean = false

    suspend fun requestStart(
        configuration: CompanionInspectionConfiguration,
        launchService: () -> Unit,
    ): AndroidInspectionBackendResult = requestLock.withLock {
        if (running) return@withLock AndroidInspectionBackendResult.Started
        val request = StartRequest(configuration, CompletableDeferred())
        if (!pendingStart.compareAndSet(null, request)) return@withLock startFailure()
        try {
            launchService()
        } catch (_: Exception) {
            pendingStart.compareAndSet(request, null)
            return@withLock startFailure()
        }
        val result = withTimeoutOrNull(operationTimeoutMillis) { request.result.await() }
        if (result == null) {
            pendingStart.compareAndSet(request, null)
            startFailure()
        } else {
            result
        }
    }

    suspend fun requestStop(stopService: () -> Unit) = requestLock.withLock {
        val start = pendingStart.getAndSet(null)
        start?.result?.complete(startFailure())
        if (!running) return@withLock
        val completion = CompletableDeferred<Unit>()
        if (!pendingStop.compareAndSet(null, completion)) return@withLock
        try {
            stopService()
            withTimeoutOrNull(operationTimeoutMillis) { completion.await() }
        } finally {
            pendingStop.compareAndSet(completion, null)
            running = false
        }
    }

    fun claimStart(): StartRequest? = pendingStart.get()

    fun completeStart(request: StartRequest, result: AndroidInspectionBackendResult): Boolean {
        if (!pendingStart.compareAndSet(request, null)) return false
        running = result == AndroidInspectionBackendResult.Started
        request.result.complete(result)
        return true
    }

    fun completeStop() {
        running = false
        pendingStop.getAndSet(null)?.complete(Unit)
    }

    internal data class StartRequest(
        val configuration: CompanionInspectionConfiguration,
        val result: CompletableDeferred<AndroidInspectionBackendResult>,
    )

    private companion object {
        private const val DEFAULT_OPERATION_TIMEOUT_MILLIS: Long = 20_000L
    }
}

private fun startFailure(): AndroidInspectionBackendResult.Failed = AndroidInspectionBackendResult.Failed(
    CompanionFailure(
        code = CompanionFailureCode.VPN_START_FAILED,
        message = "Android could not start the KNet inspection VPN.",
        recoverable = true,
    ),
)
