package com.devuloopers.knet.companion.connectivity.discovery

import android.net.nsd.NsdServiceInfo

/** API-neutral contract for monitoring service updates after DNS-SD discovery. */
internal interface AndroidCompanionServiceMonitor {
    fun observe(serviceInfo: NsdServiceInfo, generation: Long)

    fun forget(serviceInfo: NsdServiceInfo)

    fun clear()
}
