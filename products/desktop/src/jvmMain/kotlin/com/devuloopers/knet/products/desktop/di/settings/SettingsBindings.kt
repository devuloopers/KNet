package com.devuloopers.knet.products.desktop.di.settings

import com.devuloopers.knet.application.port.certificate.CertificateManagementPort
import com.devuloopers.knet.products.desktop.platform.DesktopSettingsPlatformActions
import com.devuloopers.knet.ui.desktop.settings.platform.SettingsPlatformActions
import com.devuloopers.knet.ui.desktop.settings.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Desktop settings presentation composition. */
internal val settingsBindings: Module = module {
    single<SettingsPlatformActions> { DesktopSettingsPlatformActions(get()) }
    viewModel {
        SettingsViewModel(
            widgetPreferencesRepository = get(),
            certificateManager = get<CertificateManagementPort>(),
            platformActions = get(),
            ioDispatcher = Dispatchers.IO,
        )
    }
}
