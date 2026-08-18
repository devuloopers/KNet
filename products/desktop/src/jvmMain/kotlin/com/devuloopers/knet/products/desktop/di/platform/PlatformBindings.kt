package com.devuloopers.knet.products.desktop.di.platform

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.devuloopers.knet.application.port.traffic.BodyStoreMaintenancePort
import com.devuloopers.knet.application.port.traffic.BodyStorePort
import com.devuloopers.knet.engine.session.FileBodyStore
import com.devuloopers.knet.storage.database.DatabaseFactory
import java.io.File
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.dsl.module

/** Desktop filesystem, database, and preference-store ownership. */
internal val desktopPlatformBindings: Module = module {
    single {
        val baseDir = File(System.getProperty("user.home"), ".knet")
        DatabaseFactory.create(File(baseDir, "knet.db"))
    }
    single {
        val baseDir = File(System.getProperty("user.home"), ".knet")
        val preferencesFile = File(baseDir, "workspace_prefs.preferences_pb")
        PreferenceDataStoreFactory.createWithPath(
            produceFile = { preferencesFile.absolutePath.toPath() },
        )
    }
    single { FileBodyStore(File(System.getProperty("user.home"), ".knet/bodies")) }
    single<BodyStorePort> { get<FileBodyStore>() }
    single<BodyStoreMaintenancePort> { get<FileBodyStore>() }
}
