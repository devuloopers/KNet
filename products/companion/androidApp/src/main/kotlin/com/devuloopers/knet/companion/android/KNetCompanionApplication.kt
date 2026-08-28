package com.devuloopers.knet.companion.android

import android.app.Application
import com.devuloopers.knet.companion.android.di.AndroidCompanionBootstrap
import com.devuloopers.knet.companion.android.di.CompanionAndroidModules
import com.devuloopers.knet.companion.presentation.viewmodel.CompanionViewModelDependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

/** Android companion process root that owns asynchronous Koin bootstrap for the process lifetime. */
class KNetCompanionApplication : Application() {
    private val dependencyInjectionScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var koinApplication: KoinApplication? = null
    private val dependencyInjectionDeferred: Lazy<Deferred<KoinApplication>> =
        lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            dependencyInjectionScope.async {
                val bootstrap = AndroidCompanionBootstrap.create(
                    context = this@KNetCompanionApplication,
                    persistenceScope = dependencyInjectionScope,
                )
                var startedApplication: KoinApplication? = null
                try {
                    startKoin {
                        allowOverride(false)
                        modules(CompanionAndroidModules.create(bootstrap))
                    }.also { application ->
                        startedApplication = application
                        // Resolve the complete non-UI graph before exposing the container to the Activity.
                        application.koin.get<CompanionViewModelDependencies>()
                        koinApplication = application
                    }
                } catch (failure: Throwable) {
                    if (startedApplication == null) {
                        bootstrap.platformAdapters.close()
                    } else {
                        stopKoin()
                    }
                    throw failure
                }
            }
        }

    /** Waits until restored Android dependencies are registered in the process Koin container. */
    internal suspend fun awaitDependencyInjection() {
        dependencyInjectionDeferred.value.await()
    }

    /** Stops Koin and releases callback-owning singleton definitions during emulator/test process teardown. */
    override fun onTerminate() {
        if (koinApplication != null) stopKoin()
        dependencyInjectionScope.cancel()
        super.onTerminate()
    }
}
