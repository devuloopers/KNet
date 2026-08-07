package com.devuloopers.knet.data.desktop.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.devuloopers.knet.core.http.client.KNetApiClient
import com.devuloopers.knet.core.http.execution.HttpExecutor
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutor as DomainHttpExecutor
import com.devuloopers.knet.data.desktop.apistudio.autocomplete.ProxyHistoryHeaderLookup
import com.devuloopers.knet.data.desktop.apistudio.repository.CollectionsRepositoryImpl
import com.devuloopers.knet.data.desktop.core.KNetCoreRepository
import com.devuloopers.knet.data.desktop.inspector.repository.InspectorRepositoryImpl
import com.devuloopers.knet.data.desktop.traffic.repository.LiveTrafficRepositoryImpl
import com.devuloopers.knet.data.desktop.proxy.repository.ProxyEngineRepositoryImpl
import com.devuloopers.knet.data.desktop.rules.repository.RulesRepositoryImpl
import com.devuloopers.knet.data.desktop.runtime.CertificateRuntimeRepository
import com.devuloopers.knet.data.desktop.runtime.ProxyRuntimeRepository
import com.devuloopers.knet.data.desktop.runtime.SessionRuntimeRepository
import com.devuloopers.knet.data.desktop.workspace.repository.WidgetPreferencesRepositoryImpl
import com.devuloopers.knet.domain.clientNetwork.model.ProxyTrafficListener
import com.devuloopers.knet.domain.collection.repository.CollectionsRepository
import com.devuloopers.knet.domain.collection.usecase.SaveLiveTransactionToCollectionUseCase
import com.devuloopers.knet.domain.inspector.repository.InspectorRepository
import com.devuloopers.knet.domain.proxy.repository.ProxyEngineRepository
import com.devuloopers.knet.domain.proxy.usecase.ObserveProxyEngineStateUseCase
import com.devuloopers.knet.domain.proxy.usecase.StartProxyEngineUseCase
import com.devuloopers.knet.domain.proxy.usecase.StopProxyEngineUseCase
import com.devuloopers.knet.domain.rules.repository.RulesRepository
import com.devuloopers.knet.domain.rules.usecase.GetRulesUseCase
import com.devuloopers.knet.domain.rules.usecase.SaveRuleUseCase
import com.devuloopers.knet.domain.rules.usecase.ToggleRuleUseCase
import com.devuloopers.knet.domain.traffic.repository.LiveTrafficRepository
import com.devuloopers.knet.domain.traffic.usecase.ClearLiveTrafficUseCase
import com.devuloopers.knet.domain.traffic.usecase.GetLiveTrafficUseCase
import com.devuloopers.knet.domain.traffic.usecase.LoadTransactionBodyUseCase
import com.devuloopers.knet.domain.traffic.usecase.RecordClientTransactionUseCase
import com.devuloopers.knet.domain.workspace.repository.WidgetPreferencesRepository
import com.devuloopers.knet.domain.workspace.usecase.GetWorkspaceLayoutUseCase
import com.devuloopers.knet.domain.workspace.usecase.SaveWorkspaceLayoutUseCase
import com.devuloopers.knet.storage.database.DatabaseFactory
import com.devuloopers.knet.storage.database.KNetDatabase
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File
import okio.Path.Companion.toPath

/**
 * Desktop Data Layer Koin Dependency Injection Registry.
 * Organizes runtime, datasource, repository, and usecase modules.
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
        single { 
            val proxyEngineRepository = get<ProxyEngineRepository>() as ProxyTrafficListener
            KNetApiClient(proxyTrafficListener = proxyEngineRepository)
        }
        // Core HTTP executor binding (used by core.http layer)
        single<HttpExecutor> { get<KNetApiClient>() }
        // Domain HTTP executor binding (used by domain UseCases such as ExecuteClientApiRequestUseCase)
        single<DomainHttpExecutor> { get<KNetApiClient>() }
    }

    public val repositories: Module = module {
        single<CollectionsRepository> {
            val db: KNetDatabase = get()
            CollectionsRepositoryImpl(db.collectionDao())
        }
        single<LiveTrafficRepository> { LiveTrafficRepositoryImpl(get()) }
        single<ProxyEngineRepository> { ProxyEngineRepositoryImpl(get(), get()) }
        single<InspectorRepository> { InspectorRepositoryImpl(get()) }
        single<RulesRepository> { RulesRepositoryImpl() }
        single<WidgetPreferencesRepository> { WidgetPreferencesRepositoryImpl(get()) }
        single { ProxyHistoryHeaderLookup(get()) }
    }

    public val useCases: Module = module {
        factory { GetWorkspaceLayoutUseCase(get()) }
        factory { SaveWorkspaceLayoutUseCase(get()) }
        factory { GetLiveTrafficUseCase(get()) }
        factory { ClearLiveTrafficUseCase(get()) }
        factory { LoadTransactionBodyUseCase(get()) }
        factory { StartProxyEngineUseCase(get()) }
        factory { StopProxyEngineUseCase(get()) }
        factory { ObserveProxyEngineStateUseCase(get()) }
        factory { GetRulesUseCase(get()) }
        factory { SaveRuleUseCase(get()) }
        factory { ToggleRuleUseCase(get()) }
        factory { SaveLiveTransactionToCollectionUseCase(get()) }
        factory { RecordClientTransactionUseCase(get()) }
    }

    /**
     * Aggregated list of all desktop data layer DI modules.
     */
    public val all: List<Module> = listOf(
        datasource,
        runtime,
        repositories,
        useCases
    )
}
