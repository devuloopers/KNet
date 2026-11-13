package com.devuloopers.knet.data.desktop.workspace.repository

import com.devuloopers.knet.application.contract.device.DeviceTrafficHistory
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.storage.capture.dao.CanonicalCaptureDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Room-backed device tab membership; the result contains identities only, never traffic payloads. */
class DesktopDeviceTrafficHistoryAdapter(private val dao: CanonicalCaptureDao) : DeviceTrafficHistory {
    override fun observeDeviceIdsWithTraffic(): Flow<Set<RegisteredDeviceId>> = dao.observeDeviceIdsWithTraffic()
        .map { identities -> identities.filter(String::isNotBlank).map(::RegisteredDeviceId).toSet() }
        .distinctUntilChanged()
}
