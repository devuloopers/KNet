package com.devuloopers.knet.companion.data.ios

import com.devuloopers.knet.companion.data.store.COMPANION_DATA_STORE_FILE_NAME
import com.devuloopers.knet.companion.data.store.CompanionPersistenceStores
import com.devuloopers.knet.companion.data.store.DataStoreCompanionRecordStore
import com.devuloopers.knet.companion.data.store.DataStoreCompanionSecretStore
import com.devuloopers.knet.companion.data.store.createCompanionDataStore
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/** iOS path and Keychain boundary for the shared companion DataStore. */
@OptIn(ExperimentalForeignApi::class)
public object IosCompanionDataStoreFactory {
    public suspend fun create(scope: CoroutineScope): CompanionPersistenceStores {
        val applicationSupport = checkNotNull(
            NSSearchPathForDirectoriesInDomains(
                directory = NSApplicationSupportDirectory,
                domainMask = NSUserDomainMask,
                expandTilde = true,
            ).firstOrNull() as? String,
        ) { "iOS Application Support directory is unavailable." }
        val companionDirectory = "$applicationSupport/KNet"
        check(
            NSFileManager.defaultManager.createDirectoryAtPath(
                path = companionDirectory,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
        ) { "Unable to create the KNet companion application directory." }

        val dataStore = createCompanionDataStore(
            path = "$companionDirectory/$COMPANION_DATA_STORE_FILE_NAME".toPath(),
            scope = scope,
        )
        return CompanionPersistenceStores(
            records = DataStoreCompanionRecordStore.open(dataStore, scope),
            secrets = DataStoreCompanionSecretStore(dataStore, IosKeychainCompanionSecretProtector()),
        )
    }
}
