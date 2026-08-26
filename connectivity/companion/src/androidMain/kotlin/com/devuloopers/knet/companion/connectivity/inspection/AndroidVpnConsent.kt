package com.devuloopers.knet.companion.connectivity.inspection

import android.content.Context
import android.net.VpnService

/** Android VPN consent check kept outside shared state so no native intent crosses the KMP boundary. */
internal fun interface AndroidVpnConsent {
    /** Returns whether Android has already granted VPN preparation consent to this application. */
    fun isGranted(): Boolean
}

/** Production Android consent adapter backed by [VpnService]. */
internal class PlatformAndroidVpnConsent(context: Context) : AndroidVpnConsent {
    private val applicationContext: Context = context.applicationContext

    /** Queries [VpnService.prepare] without retaining or exposing its platform intent. */
    override fun isGranted(): Boolean = VpnService.prepare(applicationContext) == null
}
