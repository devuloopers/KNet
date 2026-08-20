package com.devuloopers.knet.data.desktop.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.devuloopers.knet.domain.settings.model.ApplicationSettings
import com.devuloopers.knet.domain.settings.model.ProxyPort
import com.devuloopers.knet.domain.settings.repository.ApplicationSettingsRepository
import com.devuloopers.knet.scripting.model.ScriptLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.seconds

/**
 * DataStore-backed persistence for process-level application settings.
 *
 * Runtime components are deliberately absent: reading or writing preferences has no engine side effects.
 *
 * @param dataStore Desktop preference store shared with workspace layout persistence.
 */
class DataStoreApplicationSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : ApplicationSettingsRepository {
    private companion object {
        private val keyScriptLanguage = stringPreferencesKey("script_language")
        private val keyProxyPort = intPreferencesKey("proxy_port")
        private val keyAutoClearTraffic = booleanPreferencesKey("auto_clear_traffic_startup")
        private val keyApiStudioTimeout = intPreferencesKey("api_studio_timeout_seconds")
        private val keyLiveInterceptionTimeout = intPreferencesKey("live_interception_timeout_seconds")
    }

    override val settings: Flow<ApplicationSettings> = dataStore.data.map(::readSettings)

    override suspend fun update(transform: (ApplicationSettings) -> ApplicationSettings) {
        dataStore.edit { preferences ->
            val settings = transform(readSettings(preferences))
            preferences[keyScriptLanguage] = settings.defaultScriptLanguage.name
            preferences[keyProxyPort] = settings.proxyPort.value
            preferences[keyAutoClearTraffic] = settings.autoClearTrafficOnStartup
            preferences[keyApiStudioTimeout] = settings.apiStudioTimeout.inWholeSeconds.toInt()
            preferences[keyLiveInterceptionTimeout] = settings.liveInterceptionTimeout.inWholeSeconds.toInt()
        }
    }

    private fun readSettings(preferences: Preferences): ApplicationSettings {
        val defaults = ApplicationSettings()
        val proxyPort = preferences[keyProxyPort]
            ?.takeIf { it in ProxyPort.MINIMUM_VALUE..ProxyPort.MAXIMUM_VALUE }
            ?.let(::ProxyPort)
            ?: defaults.proxyPort
        val scriptLanguage = preferences[keyScriptLanguage]
            ?.let { persisted ->
                ScriptLanguage.entries.firstOrNull { language ->
                    language.name.equals(persisted, ignoreCase = true)
                }
            }
            ?: defaults.defaultScriptLanguage
        val apiTimeout = preferences[keyApiStudioTimeout]
            ?.seconds
            ?.coerceIn(ApplicationSettings.MINIMUM_TIMEOUT, ApplicationSettings.MAXIMUM_TIMEOUT)
            ?: defaults.apiStudioTimeout
        val liveTimeout = preferences[keyLiveInterceptionTimeout]
            ?.seconds
            ?.coerceIn(ApplicationSettings.MINIMUM_TIMEOUT, ApplicationSettings.MAXIMUM_TIMEOUT)
            ?: defaults.liveInterceptionTimeout

        return ApplicationSettings(
            proxyPort = proxyPort,
            autoClearTrafficOnStartup = preferences[keyAutoClearTraffic] ?: defaults.autoClearTrafficOnStartup,
            defaultScriptLanguage = scriptLanguage,
            apiStudioTimeout = apiTimeout,
            liveInterceptionTimeout = liveTimeout,
        )
    }
}
