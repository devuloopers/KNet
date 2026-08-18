package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.application.port.traffic.BodyStorePort
import com.devuloopers.knet.application.port.traffic.BodyStoreMaintenancePort
import com.devuloopers.knet.application.port.traffic.CaptureIngressLimits
import com.devuloopers.knet.storage.database.KNetDatabase
import com.devuloopers.knet.traffic.id.CaptureSessionId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Opens canonical sessions used by streaming proxy capture and direct application recording.
 *
 * The factory owns no active session and can therefore be reused across proxy restart cycles.
 * Each open call creates a fresh session, reconciles abandoned temporary body objects, and
 * initializes exactly one [CanonicalSessionWriter]. Real connection identities are admitted later
 * through [StreamingProxyCaptureSession].
 *
 * @property database Room database containing the current canonical tables.
 * @property bodyStore Opaque atomic body store shared by writer, query, and maintenance adapters.
 * @property bodyStoreMaintenance Finalized-object inventory and storage-key boundary.
 * @property limits Pre-allocation metadata and byte limits applied to every opened session.
 */
@OptIn(ExperimentalUuidApi::class)
class CanonicalCaptureSessionFactory(
    private val database: KNetDatabase,
    private val bodyStore: BodyStorePort,
    private val bodyStoreMaintenance: BodyStoreMaintenancePort,
    private val limits: CaptureIngressLimits = DEFAULT_LIMITS,
) {
    private val startupRecoveryMutex = Mutex()
    private var startupRecoveryComplete = false

    /**
     * Opens a canonical session whose real connection/exchange identities are supplied by the
     * streaming proxy capture sink as real downstream connections are admitted.
     */
    suspend fun openStreamingProxy(
        localListenerPort: Int,
        startedAtEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
    ): StreamingProxyCaptureSession {
        require(localListenerPort in 1..65_535) { "Canonical capture listener port must be valid." }
        require(startedAtEpochMillis >= 0L) { "Canonical capture session timestamp must not be negative." }
        recoverStartupStateOnce(startedAtEpochMillis)
        val sessionId = CaptureSessionId("desktop-${Uuid.random()}")
        val writer = openWriter(sessionId, startedAtEpochMillis)
        return StreamingProxyCaptureSession(
            sessionId = sessionId,
            ingress = writer,
            limits = limits,
        )
    }

    /**
     * Opens one canonical session for direct API Studio execution while no proxy listener is active.
     *
     * The same writer, schema, body store, query, retention, and recovery paths are used; only the
     * typed synthetic source endpoint differs.
     *
     * @param startedAtEpochMillis Session start time used for durable ordering.
     * @return Initialized direct-recording adapter.
     */
    suspend fun openDirect(
        startedAtEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
    ): StreamingProxyCaptureSession {
        require(startedAtEpochMillis >= 0L) { "Canonical capture session timestamp must not be negative." }
        recoverStartupStateOnce(startedAtEpochMillis)
        val sessionId = CaptureSessionId("api-studio-${Uuid.random()}")
        return StreamingProxyCaptureSession(
            sessionId = sessionId,
            ingress = openWriter(sessionId, startedAtEpochMillis),
            limits = limits,
        )
    }

    /** Creates and durably opens the sole writer for one newly allocated session. */
    private suspend fun openWriter(
        sessionId: CaptureSessionId,
        startedAtEpochMillis: Long,
    ): CanonicalSessionWriter = CanonicalSessionWriter.open(
        sessionId = sessionId,
        startedAtEpochMillis = startedAtEpochMillis,
        dao = database.canonicalCaptureDao(),
        bodyStore = bodyStore,
        bodyStoreMaintenance = bodyStoreMaintenance,
        limits = limits,
    )

    /** Runs process-start recovery once so live session rotation cannot terminalize the old writer. */
    private suspend fun recoverStartupStateOnce(recoveredAtEpochMillis: Long) {
        startupRecoveryMutex.withLock {
            if (startupRecoveryComplete) return
            CanonicalStartupRecovery(
                dao = database.canonicalCaptureDao(),
                bodyStore = bodyStore,
                bodyStoreMaintenance = bodyStoreMaintenance,
            ).recover(
                recoveredAtEpochMillis = recoveredAtEpochMillis,
            )
            startupRecoveryComplete = true
        }
    }

    companion object {
        /** Conservative production bounds for streaming proxy and direct capture. */
        val DEFAULT_LIMITS: CaptureIngressLimits = CaptureIngressLimits(
            metadataEventsInFlight = 4_096,
            bodyBytesInFlight = 16L * 1_024L * 1_024L,
            perBodyStoredBytes = 10L * 1_024L * 1_024L,
            maximumChunkBytes = 64 * 1_024,
        )
    }
}
