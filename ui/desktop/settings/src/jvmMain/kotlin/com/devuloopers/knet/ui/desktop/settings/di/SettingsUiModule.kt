package com.devuloopers.knet.ui.desktop.settings.di

import com.devuloopers.knet.engine.certificate.CertificateManager
import com.devuloopers.knet.ui.desktop.settings.viewmodel.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module providing UI dependencies for the Settings feature.
 */
val settingsUiModule = module {
    viewModel {
        SettingsViewModel(
            widgetPreferencesRepository = get(),
            certificateManager = get<CertificateManager>()
        )
    }
}
