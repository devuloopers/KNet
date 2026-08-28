package com.devuloopers.knet.companion.data.android

import android.content.Context
import com.devuloopers.knet.companion.data.store.COMPANION_DATA_STORE_FILE_NAME
import com.devuloopers.knet.companion.data.store.CompanionPersistenceStores
import com.devuloopers.knet.companion.data.store.DataStoreCompanionRecordStore
import com.devuloopers.knet.companion.data.store.DataStoreCompanionSecretStore
import com.devuloopers.knet.companion.data.store.createCompanionDataStore
import kotlinx.coroutines.CoroutineScope
import okio.Path.Companion.toPath

/** Android path boundary for the shared companion DataStore. */
public object AndroidCompanionDataStoreFactory {
    public suspend fun create(
        context: Context,
        scope: CoroutineScope,
    ): CompanionPersistenceStores {
        val applicationContext = context.applicationContext
        val dataStore = createCompanionDataStore(
            path = applicationContext.filesDir.resolve(COMPANION_DATA_STORE_FILE_NAME).absolutePath.toPath(),
            scope = scope,
        )
        return CompanionPersistenceStores(
            records = DataStoreCompanionRecordStore.open(dataStore, scope),
            secrets = DataStoreCompanionSecretStore(
                dataStore = dataStore,
                protector = AndroidKeystoreCompanionSecretProtector(),
            ),
        )
    }
}
