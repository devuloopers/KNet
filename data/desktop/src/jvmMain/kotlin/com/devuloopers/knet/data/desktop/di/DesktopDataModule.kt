package com.devuloopers.knet.data.desktop.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.devuloopers.knet.core.http.client.KNetApiClient
import com.devuloopers.knet.core.http.execution.HttpExecutor
import com.devuloopers.knet.data.desktop.apistudio.autocomplete.ProxyHistoryHeaderLookup
import com.devuloopers.knet.data.desktop.apistudio.repository.CollectionsRepositoryImpl
import com.devuloopers.knet.data.desktop.core.KNetCoreRepository
import com.devuloopers.knet.data.desktop.inspector.repository.InspectorRepositoryImpl
import com.devuloopers.knet.data.desktop.network.repository.NetworkRepositoryImpl
import com.devuloopers.knet.data.desktop.proxy.repository.ProxyEngineRepositoryImpl
import com.devuloopers.knet.data.desktop.rules.repository.RulesRepositoryImpl
import com.devuloopers.knet.data.desktop.runtime.CertificateRuntimeRepository
import com.devuloopers.knet.data.desktop.runtime.ProxyRuntimeRepository
import com.devuloopers.knet.data.desktop.runtime.SessionRuntimeRepository
import com.devuloopers.knet.data.desktop.traffic.repository.LiveTrafficRepositoryImpl
import com.devuloopers.knet.data.desktop.workspace.repository.WidgetPreferencesRepositoryImpl
import com.devuloopers.knet.domain.clientNetwork.model.ProxyTrafficListener
import com.devuloopers.knet.domain.collection.repository.CollectionsRepository
import com.devuloopers.knet.domain.collection.usecase.SaveLiveTransactionToCollectionUseCase
import com.devuloopers.knet.domain.inspector.repository.InspectorRepository
import com.devuloopers.knet.domain.network.repository.NetworkRepository
import com.devuloopers.knet.domain.network.usecase.GetLocalIpUseCase
import com.devuloopers.knet.domain.network.usecase.ObserveLocalIpUseCase
import com.devuloopers.knet.domain.protocol.inspector.registry.ProtocolInspectorRegistry
import com.devuloopers.knet.domain.proxy.repository.ProxyEngineRepository
import com.devuloopers.knet.domain.proxy.usecase.ObserveProxyEngineStateUseCase
import com.devuloopers.knet.domain.proxy.usecase.StartProxyEngineUseCase
import com.devuloopers.knet.domain.proxy.usecase.StopProxyEngineUseCase
import com.devuloopers.knet.domain.rules.repository.RulesRepository
import com.devuloopers.knet.domain.rules.usecase.*
import com.devuloopers.knet.domain.traffic.repository.LiveTrafficRepository
import com.devuloopers.knet.domain.traffic.usecase.*
import com.devuloopers.knet.domain.workspace.repository.WidgetPreferencesRepository
import com.devuloopers.knet.domain.workspace.usecase.GetWorkspaceLayoutUseCase
import com.devuloopers.knet.domain.workspace.usecase.SaveWorkspaceLayoutUseCase
import com.devuloopers.knet.engine.certificate.CertificateManager
import com.devuloopers.knet.engine.certificate.CertificateManagerImpl
import com.devuloopers.knet.engine.formatter.BodyFormatter
import com.devuloopers.knet.engine.formatter.formatters.GraphQLBodyFormatter
import com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLProtocolInspector
import com.devuloopers.knet.engine.proxy.network.LocalIpResolver
import com.devuloopers.knet.storage.database.DatabaseFactory
import com.devuloopers.knet.storage.database.KNetDatabase
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutor as DomainHttpExecutor

/**
 * Desktop Data Layer Koin Dependency Injection Registry.
 * Organizes runtime, datasource, repository, and usecase modules.
 */
object DesktopDataModule {

    val datasource: Module = module {
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

    val runtime: Module = module {
        single {
            val baseDir = File(System.getProperty("user.home"), ".knet")
            CertificateRuntimeRepository(baseDir)
        }
        single<CertificateManager> {
            val certRepo: CertificateRuntimeRepository = get()
            val certificatesDir = File(System.getProperty("user.home"), ".knet/certificates")
            CertificateManagerImpl(
                ca = certRepo.certificateAuthority,
                certificatesDir = certificatesDir
            )
        }
        single {
            val certRepo: CertificateRuntimeRepository = get()
            val certificateManager: CertificateManager = get()
            ProxyRuntimeRepository(
                certificateAuthority = certRepo.certificateAuthority,
                certificateCache = certRepo.certificateCache,
                keyManagerProvider = certificateManager::getKeyManagerFactory
            )
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
        single { LocalIpResolver() }
        single {
            ProtocolInspectorRegistry(
                inspectors = listOf(
                    GraphQLProtocolInspector()
                )
            )
        }
    }

    val repositories: Module = module {
        single<CollectionsRepository> {
            val db: KNetDatabase = get()
            CollectionsRepositoryImpl(db.collectionDao())
        }
        single<LiveTrafficRepository> { LiveTrafficRepositoryImpl(get()) }
        single<ProxyEngineRepository> { ProxyEngineRepositoryImpl(get(), get(), get(), get()) }
        single<InspectorRepository> { InspectorRepositoryImpl(get()) }
        single<RulesRepository> {
            val db: KNetDatabase = get()
            RulesRepositoryImpl(db.breakpointRuleDao())
        }
        single<WidgetPreferencesRepository> { WidgetPreferencesRepositoryImpl(get()) }
        single<NetworkRepository> { NetworkRepositoryImpl(get()) }
        single { ProxyHistoryHeaderLookup(get()) }
    }

    val useCases: Module = module {
        factory { GetWorkspaceLayoutUseCase(get()) }
        factory { SaveWorkspaceLayoutUseCase(get()) }
        factory { GetLiveTrafficUseCase(get()) }
        factory { ClearLiveTrafficUseCase(get()) }
        factory { LoadTransactionBodyUseCase(get()) }
        factory { ExportTrafficToSpecUseCase(get()) }
        factory { StartProxyEngineUseCase(get()) }
        factory { StopProxyEngineUseCase(get()) }
        factory { ObserveProxyEngineStateUseCase(get()) }
        factory { GetRulesUseCase(get()) }
        factory { SaveRuleUseCase(get()) }
        factory { ToggleRuleUseCase(get()) }
        factory { DeleteRuleUseCase(get()) }
        factory { ObserveGlobalInterceptionUseCase(get()) }
        factory { ToggleGlobalInterceptionUseCase(get()) }
        factory { SaveLiveTransactionToCollectionUseCase(get()) }
        factory { RecordClientTransactionUseCase(get()) }
        factory { ObserveLocalIpUseCase(get()) }
        factory { GetLocalIpUseCase(get()) }
    }

    /**
     * Aggregated list of all desktop data layer DI modules.
     */
    val all: List<Module> = listOf(
        datasource,
        runtime,
        repositories,
        useCases
    )
}
