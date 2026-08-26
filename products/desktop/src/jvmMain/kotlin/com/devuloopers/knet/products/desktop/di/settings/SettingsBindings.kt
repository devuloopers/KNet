package com.devuloopers.knet.products.desktop.di.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.devuloopers.knet.application.contract.certificate.CertificateManagement
import com.devuloopers.knet.data.desktop.settings.DataStoreApplicationSettingsRepository
import com.devuloopers.knet.domain.settings.repository.ApplicationSettingsRepository
import com.devuloopers.knet.domain.settings.usecase.ObserveApplicationSettingsUseCase
import com.devuloopers.knet.domain.settings.usecase.UpdateApplicationSettingsUseCase
import com.devuloopers.knet.products.desktop.platform.DesktopSettingsPlatformActions
import com.devuloopers.knet.products.desktop.runtime.ApplicationSettingsRuntimeSynchronizer
import com.devuloopers.knet.ui.desktop.settings.platform.SettingsPlatformActions
import com.devuloopers.knet.ui.desktop.settings.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Desktop settings presentation composition. */
internal val settingsBindings: Module = module {
    single<ApplicationSettingsRepository> {
        DataStoreApplicationSettingsRepository(get<DataStore<Preferences>>())
    }
    factory { ObserveApplicationSettingsUseCase(get()) }
    factory { UpdateApplicationSettingsUseCase(get()) }
    single { ApplicationSettingsRuntimeSynchronizer(get(), get(), get()) }
    single<SettingsPlatformActions> { DesktopSettingsPlatformActions(get()) }
    viewModel {
        SettingsViewModel(
            observeApplicationSettings = get(),
            updateApplicationSettings = get(),
            certificateManager = get<CertificateManagement>(),
            platformActions = get(),
            ioDispatcher = Dispatchers.IO,
        )
    }
}
