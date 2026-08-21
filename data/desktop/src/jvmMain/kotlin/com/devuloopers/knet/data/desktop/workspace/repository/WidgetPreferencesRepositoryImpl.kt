package com.devuloopers.knet.data.desktop.workspace.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.devuloopers.knet.domain.workspace.model.WorkspaceLayoutSettings
import com.devuloopers.knet.domain.workspace.model.TrafficTableColumnWidths
import com.devuloopers.knet.domain.workspace.repository.WidgetPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Desktop implementation of [WidgetPreferencesRepository].
 *
 * Persists only workspace layout and active-document presentation preferences to DataStore.
 *
 * @param dataStore DataStore preferences persistence engine.
 */
class WidgetPreferencesRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
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
        private val keyTrafficSerialWidth = floatPreferencesKey("traffic_column_serial_width_dp")
        private val keyTrafficTimestampWidth = floatPreferencesKey("traffic_column_timestamp_width_dp")
        private val keyTrafficMethodWidth = floatPreferencesKey("traffic_column_method_width_dp")
        private val keyTrafficProtocolWidth = floatPreferencesKey("traffic_column_protocol_width_dp")
        private val keyTrafficSourceWidth = floatPreferencesKey("traffic_column_source_width_dp")
        private val keyTrafficHostWidth = floatPreferencesKey("traffic_column_host_width_dp")
        private val keyTrafficPathWidth = floatPreferencesKey("traffic_column_path_width_dp")
        private val keyTrafficStatusWidth = floatPreferencesKey("traffic_column_status_width_dp")
        private val keyTrafficSizeWidth = floatPreferencesKey("traffic_column_size_width_dp")
        private val keyTrafficDurationWidth = floatPreferencesKey("traffic_column_duration_width_dp")
        private val keyTrafficTypeWidth = floatPreferencesKey("traffic_column_type_width_dp")
        private val keyRequestSubTab = stringPreferencesKey("active_request_sub_tab")
        private val keyScriptPhase = stringPreferencesKey("active_script_phase")
        private val keyResponseSubTab = stringPreferencesKey("active_response_sub_tab")
        private val keyActiveSessionId = stringPreferencesKey("active_session_id")
    }

    override val settingsFlow: Flow<WorkspaceLayoutSettings> = dataStore.data.map(::readWorkspaceLayout)

    override suspend fun updateSettings(
        transform: (WorkspaceLayoutSettings) -> WorkspaceLayoutSettings,
    ) {
        dataStore.edit { preferences ->
            val settings = transform(readWorkspaceLayout(preferences))
            preferences[keyTrafficFeed] = settings.isTrafficFeedVisible
            preferences[keyInspector] = settings.isInspectorVisible
            preferences[keyRulesConsole] = settings.isRulesConsoleVisible
            preferences[keyQuickReplay] = settings.isQuickReplayVisible
            preferences[keyNotesTags] = settings.isNotesTagsVisible
            preferences[keyTrafficWidth] = settings.trafficFeedWidthDp
            preferences[keySidebarWidth] = settings.sidebarWidthDp
            preferences[keyTrayHeight] = settings.bottomTrayHeightDp
            val columnWidths = settings.trafficTableColumnWidths
            preferences[keyTrafficSerialWidth] = columnWidths.serialNumberDp
            preferences[keyTrafficTimestampWidth] = columnWidths.timestampDp
            preferences[keyTrafficMethodWidth] = columnWidths.methodDp
            preferences[keyTrafficProtocolWidth] = columnWidths.protocolDp
            preferences[keyTrafficSourceWidth] = columnWidths.sourceDp
            preferences[keyTrafficHostWidth] = columnWidths.hostDp
            val pathWidthDp = columnWidths.pathDp
            if (pathWidthDp == null) {
                preferences.remove(keyTrafficPathWidth)
            } else {
                preferences[keyTrafficPathWidth] = pathWidthDp
            }
            preferences[keyTrafficStatusWidth] = columnWidths.statusDp
            preferences[keyTrafficSizeWidth] = columnWidths.sizeDp
            preferences[keyTrafficDurationWidth] = columnWidths.durationDp
            preferences[keyTrafficTypeWidth] = columnWidths.typeDp
            preferences[keyRequestSubTab] = settings.activeRequestSubTab
            preferences[keyScriptPhase] = settings.activeScriptPhase
            preferences[keyResponseSubTab] = settings.activeResponseSubTab
            preferences[keyActiveSessionId] = settings.activeSessionId
        }
    }

    private fun readWorkspaceLayout(preferences: Preferences): WorkspaceLayoutSettings {
        val defaultColumnWidths = TrafficTableColumnWidths()
        return WorkspaceLayoutSettings(
            isTrafficFeedVisible = preferences[keyTrafficFeed] ?: true,
            isInspectorVisible = preferences[keyInspector] ?: true,
            isRulesConsoleVisible = preferences[keyRulesConsole] ?: false,
            isQuickReplayVisible = preferences[keyQuickReplay] ?: false,
            isNotesTagsVisible = preferences[keyNotesTags] ?: false,
            trafficFeedWidthDp = preferences[keyTrafficWidth] ?: 600f,
            sidebarWidthDp = preferences[keySidebarWidth] ?: 260f,
            bottomTrayHeightDp = preferences[keyTrayHeight] ?: 180f,
            trafficTableColumnWidths = TrafficTableColumnWidths(
                serialNumberDp = preferences.positiveWidthOrDefault(
                    keyTrafficSerialWidth,
                    defaultColumnWidths.serialNumberDp,
                ),
                timestampDp = preferences.positiveWidthOrDefault(
                    keyTrafficTimestampWidth,
                    defaultColumnWidths.timestampDp,
                ),
                methodDp = preferences.positiveWidthOrDefault(
                    keyTrafficMethodWidth,
                    defaultColumnWidths.methodDp,
                ),
                protocolDp = preferences.positiveWidthOrDefault(
                    keyTrafficProtocolWidth,
                    defaultColumnWidths.protocolDp,
                ),
                sourceDp = preferences.positiveWidthOrDefault(
                    keyTrafficSourceWidth,
                    defaultColumnWidths.sourceDp,
                ),
                hostDp = preferences.positiveWidthOrDefault(keyTrafficHostWidth, defaultColumnWidths.hostDp),
                pathDp = preferences[keyTrafficPathWidth]?.takeIf { width -> width.isFinite() && width > 0f },
                statusDp = preferences.positiveWidthOrDefault(keyTrafficStatusWidth, defaultColumnWidths.statusDp),
                sizeDp = preferences.positiveWidthOrDefault(keyTrafficSizeWidth, defaultColumnWidths.sizeDp),
                durationDp = preferences.positiveWidthOrDefault(
                    keyTrafficDurationWidth,
                    defaultColumnWidths.durationDp,
                ),
                typeDp = preferences.positiveWidthOrDefault(keyTrafficTypeWidth, defaultColumnWidths.typeDp),
            ),
            activeRequestSubTab = preferences[keyRequestSubTab] ?: "BODY",
            activeScriptPhase = preferences[keyScriptPhase] ?: "PRE_REQUEST",
            activeResponseSubTab = preferences[keyResponseSubTab] ?: "BODY",
            activeSessionId = preferences[keyActiveSessionId] ?: "",
        )
    }

    private fun Preferences.positiveWidthOrDefault(key: Preferences.Key<Float>, default: Float): Float =
        get(key)?.takeIf { width -> width.isFinite() && width > 0f } ?: default
}
