package com.devuloopers.knet.products.desktop.di.platform

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.devuloopers.knet.application.contract.traffic.BodyStoreMaintenance
import com.devuloopers.knet.application.contract.traffic.BodyStore
import com.devuloopers.knet.engine.session.FileBodyStore
import com.devuloopers.knet.storage.database.DatabaseFactory
import java.io.File
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.dsl.module

/** Desktop filesystem, database, and preference-store ownership. */
internal val desktopPlatformBindings: Module = module {
    single {
        val baseDir = get<com.devuloopers.knet.products.desktop.config.DesktopConfiguration>().appDirectory.toFile()
        DatabaseFactory.create(File(baseDir, "knet.db"))
    }
    single {
        val baseDir = get<com.devuloopers.knet.products.desktop.config.DesktopConfiguration>().appDirectory.toFile()
        val preferencesFile = File(baseDir, "workspace_prefs.preferences_pb")
        PreferenceDataStoreFactory.createWithPath(
            scope = get<kotlinx.coroutines.CoroutineScope>(),
            produceFile = { preferencesFile.absolutePath.toPath() },
        )
    }
    single { FileBodyStore(get<com.devuloopers.knet.products.desktop.config.DesktopConfiguration>().appDirectory.resolve("bodies").toFile()) }
    single<BodyStore> { get<FileBodyStore>() }
    single<BodyStoreMaintenance> { get<FileBodyStore>() }
}
