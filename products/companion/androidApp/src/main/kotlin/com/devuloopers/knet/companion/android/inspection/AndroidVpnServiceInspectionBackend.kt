package com.devuloopers.knet.companion.android.inspection

import android.content.Context
import android.content.Intent
import com.devuloopers.knet.companion.application.contract.CompanionInspectionConfiguration
import com.devuloopers.knet.companion.connectivity.inspection.AndroidInspectionBackend
import com.devuloopers.knet.companion.connectivity.inspection.AndroidInspectionBackendResult

/** Product adapter that asks Android to own the long-lived VPN component. */
internal class AndroidVpnServiceInspectionBackend(
    context: Context,
    private val coordinator: AndroidInspectionRuntimeCoordinator,
) : AndroidInspectionBackend {
    private val applicationContext = context.applicationContext

    override suspend fun start(
        configuration: CompanionInspectionConfiguration,
    ): AndroidInspectionBackendResult = coordinator.requestStart(configuration) {
        applicationContext.startForegroundService(serviceIntent(KNetInspectionVpnService.ACTION_START))
    }

    override suspend fun stop() {
        coordinator.requestStop {
            applicationContext.startService(serviceIntent(KNetInspectionVpnService.ACTION_STOP))
        }
    }

    private fun serviceIntent(action: String): Intent =
        Intent(applicationContext, KNetInspectionVpnService::class.java).setAction(action)
}
