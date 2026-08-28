package com.devuloopers.knet.companion.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Reactive KMP registration record store backed by the process-singleton companion DataStore. */
internal class DataStoreCompanionRecordStore private constructor(
    private val dataStore: DataStore<Preferences>,
    initialContent: String?,
    observerScope: CoroutineScope,
) : CompanionRecordStore {
    private val mutableContent: MutableStateFlow<String?> = MutableStateFlow(initialContent)

    override val content: StateFlow<String?> = mutableContent

    init {
        observerScope.launch {
            dataStore.data
                .map { preferences -> preferences[CompanionPreferenceKeys.registrationContent] }
                .distinctUntilChanged()
                .collect(mutableContent::emit)
        }
    }

    override suspend fun write(content: String?) {
        val updated = dataStore.edit { preferences ->
            if (content == null) {
                preferences.remove(CompanionPreferenceKeys.registrationContent)
            } else {
                preferences[CompanionPreferenceKeys.registrationContent] = content
            }
        }
        mutableContent.value = updated[CompanionPreferenceKeys.registrationContent]
    }

    public companion object {
        /** Opens the store only after its first durable snapshot is available. */
        suspend fun open(
            dataStore: DataStore<Preferences>,
            observerScope: CoroutineScope,
        ): DataStoreCompanionRecordStore {
            val initial = dataStore.data.first()[CompanionPreferenceKeys.registrationContent]
            return DataStoreCompanionRecordStore(dataStore, initial, observerScope)
        }
    }
}

/** Common encrypted credential persistence; plaintext only crosses the platform protector boundary. */
internal class DataStoreCompanionSecretStore(
    private val dataStore: DataStore<Preferences>,
    private val protector: CompanionSecretProtector,
) : CompanionSecretStore {
    override suspend fun write(key: String, value: String) {
        require(key.isNotBlank() && value.isNotBlank())
        val protected = protector.protect(key, value)
        dataStore.edit { preferences ->
            preferences[CompanionPreferenceKeys.secret(key)] = protected.serializedValue
        }
    }

    override suspend fun read(key: String): String? {
        require(key.isNotBlank())
        val serialized = dataStore.data.first()[CompanionPreferenceKeys.secret(key)] ?: return null
        return protector.reveal(key, ProtectedCompanionSecret(serialized))
    }

    override suspend fun remove(key: String) {
        require(key.isNotBlank())
        protector.remove(key)
        dataStore.edit { preferences -> preferences.remove(CompanionPreferenceKeys.secret(key)) }
    }
}

private object CompanionPreferenceKeys {
    val registrationContent: Preferences.Key<String> = stringPreferencesKey("registration_content_v1")

    fun secret(reference: String): Preferences.Key<String> = stringPreferencesKey(
        "protected_secret_v1.${SECRET_KEY_ENCODING.encode(reference.encodeToByteArray())}",
    )

    private val SECRET_KEY_ENCODING: Base64 =
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
}
