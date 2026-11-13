package com.devuloopers.knet.products.desktop.di.workspace

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.devuloopers.knet.data.desktop.workspace.repository.WidgetPreferencesRepositoryImpl
import com.devuloopers.knet.domain.workspace.repository.WidgetPreferencesRepository
import com.devuloopers.knet.domain.workspace.usecase.GetWorkspaceLayoutUseCase
import com.devuloopers.knet.domain.workspace.usecase.UpdateWorkspaceLayoutUseCase
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.core.module.dsl.viewModel

/** Workspace preference persistence and layout workflows. */
internal val workspaceBindings: Module = module {
    single<com.devuloopers.knet.application.contract.device.DeviceWorkspacePreferences> {
        com.devuloopers.knet.data.desktop.workspace.repository.DeviceWorkspacePreferencesAdapter(get())
    }
    single<com.devuloopers.knet.application.contract.device.DeviceTrafficHistory> {
        com.devuloopers.knet.data.desktop.workspace.repository.DesktopDeviceTrafficHistoryAdapter(
            get<com.devuloopers.knet.storage.database.KNetDatabase>().canonicalCaptureDao(),
        )
    }
    factory { com.devuloopers.knet.application.usecase.device.ObserveDeviceWorkspacesUseCase(get(), get(), get()) }
    factory { com.devuloopers.knet.application.usecase.device.ObserveDeviceWorkspaceSelectionUseCase(get()) }
    factory { com.devuloopers.knet.application.usecase.device.SelectDeviceWorkspaceUseCase(get()) }
    factory { com.devuloopers.knet.application.usecase.device.ObserveSourceTrafficUseCase(get()) }
    viewModel { com.devuloopers.knet.ui.desktop.app.workspace.DeviceWorkspaceViewModel(get(), get(), get(), get()) }
    single<WidgetPreferencesRepository> {
        WidgetPreferencesRepositoryImpl(
            dataStore = get<DataStore<Preferences>>(),
        )
    }
    factory { GetWorkspaceLayoutUseCase(get()) }
    factory { UpdateWorkspaceLayoutUseCase(get()) }
}
