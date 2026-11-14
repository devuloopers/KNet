package com.devuloopers.knet.data.desktop.traffic.repository

import com.devuloopers.knet.application.contract.traffic.TrafficSource
import com.devuloopers.knet.application.contract.traffic.TrafficMaintenance
import com.devuloopers.knet.data.desktop.capture.storageKind
import com.devuloopers.knet.storage.database.KNetDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.time.Clock

/**
 * Removes terminal metadata after the capture-writer barrier. Durable deletion-outbox inserts wake
 * the process-owned body worker; filesystem latency never owns Clear completion. Batches yield the
 * Room writer between transactions so the replacement capture session can continue accepting traffic.
 */
class DesktopTrafficMaintenanceAdapter(private val database: KNetDatabase) : TrafficMaintenance {
    override suspend fun clearTerminalTraffic() = withContext(Dispatchers.IO) {
        val dao = database.canonicalCaptureDao()
        val requestedAt = Clock.System.now().toEpochMilliseconds()
        while (true) {
            val sessions = dao.getClosedSessionClearIds(100)
            if (sessions.isEmpty()) break
            for (session in sessions) {
                dao.queueAndDeleteClosedSession(session, requestedAt)
                yield()
            }
        }
    }

    /** Deletes only the chosen source; shared connection/session metadata remains for retained siblings. */
    override suspend fun clearSourceTraffic(source: TrafficSource) {
        if (source == TrafficSource.All) return clearTerminalTraffic()
        withContext(Dispatchers.IO) {
            val dao = database.canonicalCaptureDao()
            val requestedAt = Clock.System.now().toEpochMilliseconds()
            while (dao.queueAndDeleteSourceBatch(source.storageKind(), (source as? TrafficSource.Device)?.id?.value,
                    requestedAt, 128) != 0) {
                yield()
            }
        }
    }
}
