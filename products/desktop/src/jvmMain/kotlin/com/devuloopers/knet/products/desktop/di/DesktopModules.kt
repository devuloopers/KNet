package com.devuloopers.knet.products.desktop.di

import com.devuloopers.knet.products.desktop.di.apistudio.apiStudioBindings
import com.devuloopers.knet.products.desktop.di.breakpoint.breakpointBindings
import com.devuloopers.knet.products.desktop.di.certificate.certificateBindings
import com.devuloopers.knet.products.desktop.di.connectivity.connectivityBindings
import com.devuloopers.knet.products.desktop.di.httppanel.httpPanelBindings
import com.devuloopers.knet.products.desktop.di.inspection.inspectionBindings
import com.devuloopers.knet.products.desktop.di.platform.desktopPlatformBindings
import com.devuloopers.knet.products.desktop.di.proxy.proxyBindings
import com.devuloopers.knet.products.desktop.di.request.requestDescriptorBindings
import com.devuloopers.knet.products.desktop.di.settings.settingsBindings
import com.devuloopers.knet.products.desktop.di.traffic.trafficBindings
import com.devuloopers.knet.products.desktop.di.workspace.workspaceBindings
import org.koin.core.module.Module

/**
 * The desktop product's composition root.
 *
 * Every binding is grouped by its owning feature. Infrastructure, data, application, and UI
 * modules expose types and contracts but do not define product assembly.
 */
object DesktopModules {

    val platform: List<Module> = listOf(desktopPlatformBindings)

    val features: List<Module> = listOf(
        apiStudioBindings,
        breakpointBindings,
        certificateBindings,
        connectivityBindings,
        httpPanelBindings,
        inspectionBindings,
        proxyBindings,
        requestDescriptorBindings,
        settingsBindings,
        trafficBindings,
        workspaceBindings,
    )

    /** Complete module list used only by the desktop product bootstrap. */
    val all: List<Module> = platform + features
}
