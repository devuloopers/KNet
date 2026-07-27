package com.devuloopers.knet.data.di

import com.devuloopers.knet.data.inspector.di.inspectorDataModule
import com.devuloopers.knet.data.livetraffic.di.liveTrafficDataModule
import com.devuloopers.knet.data.repository.KNetCoreRepository
import com.devuloopers.knet.data.rules.di.rulesDataModule
import com.devuloopers.knet.data.workspace.di.workspaceDataModule
import java.io.File
import org.koin.dsl.module

/**
 * Global Koin DI registry for the data module.
 * Aggregates all feature-specific data modules and core storage engines.
 */
val dataModule = module {
    single {
        val baseDir = File(System.getProperty("user.home"), ".knet")
        KNetCoreRepository.getInstance(baseDir)
    }
    includes(liveTrafficDataModule, inspectorDataModule, rulesDataModule, workspaceDataModule)
}
