package com.devuloopers.knet.data.desktop.workspace.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.devuloopers.knet.core.http.client.KNetApiClient
import com.devuloopers.knet.domain.workspace.model.WorkspaceLayoutSettings
import com.devuloopers.knet.domain.workspace.repository.WidgetPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Desktop implementation of [WidgetPreferencesRepository].
 *
 * Persists workspace preferences to DataStore and synchronizes timeout configurations
 * to low-level Netty interceptors and Ktor HTTP clients at runtime.
 *
 * @param dataStore DataStore preferences persistence engine.
 * @param apiClient Optional [KNetApiClient] HTTP client instance for timeout synchronization.
 */
class WidgetPreferencesRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
    private val apiClient: KNetApiClient? = null
) : WidgetPreferencesRepository {

    private companion object {
        private val keyTrafficFeed = booleanPreferencesKey("is_traffic_feed_visible")
        private val keyInspector = booleanPreferencesKey("is_inspector_visible")
        private val keyRulesConsole = booleanPreferencesKey("is_rules_console_visible")
        private val keyQuickReplay = booleanPreferencesKey("is_quick_replay_visible")
        private val keyNotesTags = booleanPreferencesKey("is_notes_tags_visible")
        private val keyTrafficWidth = floatPreferencesKey("traffic_feed_width_dp")
        private val keySidebarWidth = floatPreferencesKey("sidebar_width_dp")
        private val keyTrayHeight = floatPreferencesKey("bottom_tray_height_dp")
        private val keyRequestSubTab = stringPreferencesKey("active_request_sub_tab")
        private val keyScriptPhase = stringPreferencesKey("active_script_phase")
        private val keyResponseSubTab = stringPreferencesKey("active_response_sub_tab")
        private val keyActiveSessionId = stringPreferencesKey("active_session_id")
        private val keyScriptLanguage = stringPreferencesKey("script_language")
        private val keyProxyPort = intPreferencesKey("proxy_port")
        private val keyAutoClearTraffic = booleanPreferencesKey("auto_clear_traffic_startup")
        private val keyTheme = stringPreferencesKey("theme")
        private val keyMaxPayloadMb = intPreferencesKey("max_payload_mb")
        private val keyApiStudioTimeout = intPreferencesKey("api_studio_timeout_seconds")
        private val keyLiveInterceptionTimeout = intPreferencesKey("live_interception_timeout_seconds")
    }

    override val settingsFlow: Flow<WorkspaceLayoutSettings> = dataStore.data.map { preferences ->
        val liveTimeout = preferences[keyLiveInterceptionTimeout] ?: 60
        val apiStudioTimeout = preferences[keyApiStudioTimeout] ?: 60

        // Synchronize Netty and Ktor
        com.devuloopers.knet.engine.interceptor.InterceptCoordinator.setTimeoutSeconds(liveTimeout)
        apiClient?.updateTimeoutSeconds(apiStudioTimeout)

        WorkspaceLayoutSettings(
            isTrafficFeedVisible = preferences[keyTrafficFeed] ?: true,
            isInspectorVisible = preferences[keyInspector] ?: true,
            isRulesConsoleVisible = preferences[keyRulesConsole] ?: false,
            isQuickReplayVisible = preferences[keyQuickReplay] ?: false,
            isNotesTagsVisible = preferences[keyNotesTags] ?: false,
            trafficFeedWidthDp = preferences[keyTrafficWidth] ?: 600f,
            sidebarWidthDp = preferences[keySidebarWidth] ?: 260f,
            bottomTrayHeightDp = preferences[keyTrayHeight] ?: 180f,
            activeRequestSubTab = preferences[keyRequestSubTab] ?: "BODY",
            activeScriptPhase = preferences[keyScriptPhase] ?: "PRE_REQUEST",
            activeResponseSubTab = preferences[keyResponseSubTab] ?: "BODY",
            activeSessionId = preferences[keyActiveSessionId] ?: "",
            scriptLanguage = preferences[keyScriptLanguage] ?: "JAVASCRIPT",
            proxyPort = preferences[keyProxyPort] ?: 8080,
            autoClearTrafficOnStartup = preferences[keyAutoClearTraffic] ?: false,
            theme = preferences[keyTheme] ?: "DARK",
            maxPayloadMb = preferences[keyMaxPayloadMb] ?: 10,
            apiStudioTimeoutSeconds = apiStudioTimeout,
            liveInterceptionTimeoutSeconds = liveTimeout
        )
    }

    override suspend fun saveSettings(settings: WorkspaceLayoutSettings) {
        // Synchronize Netty and Ktor
        com.devuloopers.knet.engine.interceptor.InterceptCoordinator.setTimeoutSeconds(settings.liveInterceptionTimeoutSeconds)
        apiClient?.updateTimeoutSeconds(settings.apiStudioTimeoutSeconds)

        dataStore.edit { preferences ->
            preferences[keyTrafficFeed] = settings.isTrafficFeedVisible
            preferences[keyInspector] = settings.isInspectorVisible
            preferences[keyRulesConsole] = settings.isRulesConsoleVisible
            preferences[keyQuickReplay] = settings.isQuickReplayVisible
            preferences[keyNotesTags] = settings.isNotesTagsVisible
            preferences[keyTrafficWidth] = settings.trafficFeedWidthDp
            preferences[keySidebarWidth] = settings.sidebarWidthDp
            preferences[keyTrayHeight] = settings.bottomTrayHeightDp
            preferences[keyRequestSubTab] = settings.activeRequestSubTab
            preferences[keyScriptPhase] = settings.activeScriptPhase
            preferences[keyResponseSubTab] = settings.activeResponseSubTab
            preferences[keyActiveSessionId] = settings.activeSessionId
            preferences[keyScriptLanguage] = settings.scriptLanguage
            preferences[keyProxyPort] = settings.proxyPort
            preferences[keyAutoClearTraffic] = settings.autoClearTrafficOnStartup
            preferences[keyTheme] = settings.theme
            preferences[keyMaxPayloadMb] = settings.maxPayloadMb
            preferences[keyApiStudioTimeout] = settings.apiStudioTimeoutSeconds
            preferences[keyLiveInterceptionTimeout] = settings.liveInterceptionTimeoutSeconds
        }
    }
}
