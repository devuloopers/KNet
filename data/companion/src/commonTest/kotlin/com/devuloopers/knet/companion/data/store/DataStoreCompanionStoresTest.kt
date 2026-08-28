package com.devuloopers.knet.companion.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreCompanionStoresTest {
    @Test
    fun recordStoreAwaitsInitialSnapshotAndReactsToExternalUpdates() = runTest {
        val dataStore = MemoryPreferencesDataStore()
        val store = DataStoreCompanionRecordStore.open(dataStore, backgroundScope)
        runCurrent()

        assertNull(store.content.value)

        store.write("updated")
        assertEquals("updated", store.content.value)

        dataStore.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                this[stringPreferencesKey("registration_content_v1")] = "external"
            }
        }
        runCurrent()
        assertEquals("external", store.content.value)
    }

    @Test
    fun secretStorePersistsOnlyProtectedMaterialAndRoundTripsThroughProtector() = runTest {
        val dataStore = MemoryPreferencesDataStore()
        val store = DataStoreCompanionSecretStore(dataStore, PrefixSecretProtector())

        store.write("credential-1", "plain-secret")

        assertEquals("plain-secret", store.read("credential-1"))
        val persistedValues = dataStore.data.first().asMap().values.filterIsInstance<String>()
        assertTrue(persistedValues.any { it == "sealed:credential-1:plain-secret" })
        assertFalse(persistedValues.any { it == "plain-secret" })

        store.remove("credential-1")
        assertNull(store.read("credential-1"))
    }

    private class MemoryPreferencesDataStore(
        initial: Preferences = emptyPreferences(),
    ) : DataStore<Preferences> {
        private val mutex = Mutex()
        private val mutableData = MutableStateFlow(initial)

        override val data: Flow<Preferences> = mutableData

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            mutex.withLock {
                transform(mutableData.value).also { mutableData.value = it }
            }
    }

    private class PrefixSecretProtector : CompanionSecretProtector {
        override suspend fun protect(key: String, value: String): ProtectedCompanionSecret =
            ProtectedCompanionSecret("sealed:$key:$value")

        override suspend fun reveal(key: String, secret: ProtectedCompanionSecret): String? =
            secret.serializedValue.removePrefix("sealed:$key:").takeIf { it != secret.serializedValue }
    }
}
