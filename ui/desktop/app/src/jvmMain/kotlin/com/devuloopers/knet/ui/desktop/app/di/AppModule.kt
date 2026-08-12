package com.devuloopers.knet.ui.desktop.app.di

import com.devuloopers.knet.ui.desktop.apistudio.di.apiStudioUiModule
import com.devuloopers.knet.ui.desktop.certificate.di.certificateUiModule
import com.devuloopers.knet.ui.desktop.settings.di.settingsUiModule
import com.devuloopers.knet.ui.desktop.traffic.di.trafficModule
import org.koin.dsl.module

/**
 * Koin module aggregating desktop application UI framework dependencies.
 * Includes [trafficModule], [apiStudioUiModule], [certificateUiModule], and [settingsUiModule].
 */
val desktopAppUiModule = module {
    includes(trafficModule, apiStudioUiModule, certificateUiModule, settingsUiModule)
}
