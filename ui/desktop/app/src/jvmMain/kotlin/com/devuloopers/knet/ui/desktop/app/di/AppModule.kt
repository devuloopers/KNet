package com.devuloopers.knet.ui.desktop.app.di

import com.devuloopers.knet.ui.desktop.apistudio.di.apiStudioUiModule
import com.devuloopers.knet.ui.desktop.certificate.di.certificateUiModule
import com.devuloopers.knet.ui.desktop.inspector.di.inspectorUiModule
import com.devuloopers.knet.ui.desktop.scripting.di.scriptingUiModule
import com.devuloopers.knet.ui.desktop.traffic.di.trafficUiModule
import com.devuloopers.knet.ui.desktop.workspace.di.workspaceUiModule
import org.koin.dsl.module

/**
 * Koin module aggregating all desktop-specific UI modules.
 */
public val desktopAppUiModule = module {
    includes(
        workspaceUiModule,
        trafficUiModule,
        inspectorUiModule,
        apiStudioUiModule,
        scriptingUiModule,
        certificateUiModule
    )
}
