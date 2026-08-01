package com.devuloopers.knet.data.desktop.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.devuloopers.knet.data.desktop.apistudio.autocomplete.ProxyHistoryHeaderLookup
import com.devuloopers.knet.data.desktop.apistudio.repository.CollectionsRepositoryImpl
import com.devuloopers.knet.data.desktop.core.KNetCoreRepository
import com.devuloopers.knet.data.desktop.inspector.repository.InspectorRepositoryImpl
import com.devuloopers.knet.data.desktop.traffic.repository.LiveTrafficRepositoryImpl
import com.devuloopers.knet.data.desktop.rules.repository.RulesRepositoryImpl
import com.devuloopers.knet.data.desktop.runtime.CertificateRuntimeRepository
import com.devuloopers.knet.data.desktop.runtime.ProxyRuntimeRepository
import com.devuloopers.knet.data.desktop.runtime.SessionRuntimeRepository
import com.devuloopers.knet.data.desktop.workspace.repository.WidgetPreferencesRepositoryImpl
import com.devuloopers.knet.domain.collection.repository.CollectionsRepository
import com.devuloopers.knet.domain.inspector.repository.InspectorRepository
import com.devuloopers.knet.domain.rules.repository.RulesRepository
import com.devuloopers.knet.domain.traffic.repository.LiveTrafficRepository
import com.devuloopers.knet.domain.workspace.repository.WidgetPreferencesRepository
import com.devuloopers.knet.storage.database.DatabaseFactory
import com.devuloopers.knet.storage.database.KNetDatabase
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File
import okio.Path.Companion.toPath

/**
 * Desktop Data Layer Koin Dependency Injection Registry.
 * Organizes runtime, datasource, and repository modules.
 */
public object DesktopDataModule {

    public val datasource: Module = module {
        single {
            val baseDir = File(System.getProperty("user.home"), ".knet")
            val dbFile = File(baseDir, "knet.db")
            DatabaseFactory.create(dbFile)
        }
        single {
            val baseDir = File(System.getProperty("user.home"), ".knet")
            val prefsFile = File(baseDir, "workspace_prefs.preferences_pb")
            PreferenceDataStoreFactory.createWithPath(
                produceFile = { prefsFile.absolutePath.toPath() }
            )
        }
    }

    public val runtime: Module = module {
        single {
            val baseDir = File(System.getProperty("user.home"), ".knet")
            CertificateRuntimeRepository(baseDir)
        }
        single {
            val certRepo: CertificateRuntimeRepository = get()
            ProxyRuntimeRepository(certRepo.certificateAuthority, certRepo.certificateCache)
        }
        single {
            val baseDir = File(System.getProperty("user.home"), ".knet")
            SessionRuntimeRepository(get(), baseDir)
        }
        single {
            KNetCoreRepository(get(), get(), get())
        }
    }

    public val repositories: Module = module {
        single<CollectionsRepository> {
            val db: KNetDatabase = get()
            CollectionsRepositoryImpl(db.collectionDao())
        }
        single<LiveTrafficRepository> { LiveTrafficRepositoryImpl(get()) }
        single<InspectorRepository> { InspectorRepositoryImpl(get()) }
        single<RulesRepository> { RulesRepositoryImpl() }
        single<WidgetPreferencesRepository> { WidgetPreferencesRepositoryImpl(get()) }
        single { ProxyHistoryHeaderLookup(get()) }
    }

    /**
     * Aggregated list of all desktop data layer DI modules.
     */
    public val all: List<Module> = listOf(
        datasource,
        runtime,
        repositories
    )
}
