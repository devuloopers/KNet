package com.devuloopers.knet.data.workspace.di

import com.devuloopers.knet.data.workspace.repository.WidgetPreferencesRepositoryImpl
import com.devuloopers.knet.domain.workspace.repository.WidgetPreferencesRepository
import com.devuloopers.knet.storage.WorkspacePreferencesDataSource
import java.io.File
import org.koin.dsl.module

val workspaceDataModule = module {
    single { WorkspacePreferencesDataSource(File(System.getProperty("user.home"), ".knet")) }
    single<WidgetPreferencesRepository> { WidgetPreferencesRepositoryImpl(get()) }
}
