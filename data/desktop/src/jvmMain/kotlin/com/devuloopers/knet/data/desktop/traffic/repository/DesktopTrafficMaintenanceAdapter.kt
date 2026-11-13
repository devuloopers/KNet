package com.devuloopers.knet.data.desktop.traffic.repository

import com.devuloopers.knet.application.contract.traffic.BodyStore
import com.devuloopers.knet.application.contract.traffic.TrafficSource
import com.devuloopers.knet.application.contract.traffic.TrafficMaintenance
import com.devuloopers.knet.data.desktop.capture.storageKind
import kotlinx.coroutines.yield
import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.core.logger.LogTags
import com.devuloopers.knet.data.desktop.capture.BodyDeletionReconciler
import com.devuloopers.knet.data.desktop.capture.CanonicalRetentionManager
import com.devuloopers.knet.data.desktop.capture.CanonicalRetentionPolicy
import com.devuloopers.knet.storage.database.KNetDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/** Clears terminal canonical sessions after application-level active-session rotation. */
class DesktopTrafficMaintenanceAdapter(
    private val database: KNetDatabase,
    private val canonicalBodyStore: BodyStore,
) : TrafficMaintenance {
    override suspend fun clearTerminalTraffic() {
        withContext(Dispatchers.IO) {
            val retention = CanonicalRetentionManager(
                dao = database.canonicalCaptureDao(),
                deletionReconciler = BodyDeletionReconciler(database.canonicalCaptureDao(), canonicalBodyStore),
            )
            var pass = 0
            var requiresAnotherPass: Boolean
            do {
                val result = retention.enforce(
                    policy = CanonicalRetentionPolicy(
                        maximumClosedSessions = 0,
                        maximumStoredBytes = 0L,
                        maximumSessionsPerRun = MAXIMUM_CLEAR_SESSIONS_PER_PASS,
                    ),
                    requestedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                )
                requiresAnotherPass = result.requiresAnotherPass
                pass += 1
            } while (requiresAnotherPass && pass < MAXIMUM_CLEAR_PASSES)
            if (requiresAnotherPass) {
                KNetLogger.warn(tag = LogTags.TRAFFIC) {
                    "Traffic clear reached its bounded maintenance pass limit; remaining sessions will retry."
                }
            }
        }
    }

    /** Reuses the global writer barrier but never deletes a shared session or another source's rows. */
    override suspend fun clearSourceTraffic(source: TrafficSource) {
        if (source == TrafficSource.All) return clearTerminalTraffic()
        withContext(Dispatchers.IO) {
            val dao = database.canonicalCaptureDao()
            val requestedAt = Clock.System.now().toEpochMilliseconds()
            while (dao.queueAndDeleteSourceBatch(source.storageKind(), (source as? TrafficSource.Device)?.id?.value,
                    requestedAt, 128) != 0) {
                BodyDeletionReconciler(dao, canonicalBodyStore).reconcile(256)
                yield()
            }
            BodyDeletionReconciler(dao, canonicalBodyStore).reconcile(256)
        }
    }

    private companion object {
        private const val MAXIMUM_CLEAR_SESSIONS_PER_PASS = 100
        private const val MAXIMUM_CLEAR_PASSES = 100
    }
}
