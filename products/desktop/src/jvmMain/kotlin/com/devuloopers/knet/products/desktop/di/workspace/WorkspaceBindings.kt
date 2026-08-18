package com.devuloopers.knet.products.desktop.di.workspace

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.devuloopers.knet.data.desktop.workspace.repository.WidgetPreferencesRepositoryImpl
import com.devuloopers.knet.domain.workspace.repository.WidgetPreferencesRepository
import com.devuloopers.knet.domain.workspace.usecase.GetWorkspaceLayoutUseCase
import com.devuloopers.knet.domain.workspace.usecase.SaveWorkspaceLayoutUseCase
import org.koin.core.module.Module
import org.koin.dsl.module

/** Workspace preference persistence and layout workflows. */
internal val workspaceBindings: Module = module {
    single<WidgetPreferencesRepository> {
        WidgetPreferencesRepositoryImpl(
            dataStore = get<DataStore<Preferences>>(),
            apiClient = get(),
            breakpointControl = get(),
        )
    }
    factory { GetWorkspaceLayoutUseCase(get()) }
    factory { SaveWorkspaceLayoutUseCase(get()) }
}
