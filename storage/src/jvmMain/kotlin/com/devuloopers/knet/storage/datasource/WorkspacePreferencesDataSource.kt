package com.devuloopers.knet.storage.datasource

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore preferences datasource for persisting workspace panel layout settings.
 */
public class WorkspacePreferencesDataSource(
    private val dataStore: DataStore<Preferences>
) {
    private companion object {
        private val keyTrafficFeed = booleanPreferencesKey("is_traffic_feed_visible")
        private val keyInspector = booleanPreferencesKey("is_inspector_visible")
        private val keyRulesConsole = booleanPreferencesKey("is_rules_console_visible")
        private val keyQuickReplay = booleanPreferencesKey("is_quick_replay_visible")
        private val keyNotesTags = booleanPreferencesKey("is_notes_tags_visible")
        private val keyTrafficWidth = floatPreferencesKey("traffic_feed_width_dp")
        private val keySidebarWidth = floatPreferencesKey("sidebar_width_dp")
        private val keyTrayHeight = floatPreferencesKey("bottom_tray_height_dp")
    }

    public val settingsFlow: Flow<Map<String, Any>> = dataStore.data.map { preferences ->
        mapOf(
            "isTrafficFeedVisible" to (preferences[keyTrafficFeed] ?: true),
            "isInspectorVisible" to (preferences[keyInspector] ?: true),
            "isRulesConsoleVisible" to (preferences[keyRulesConsole] ?: false),
            "isQuickReplayVisible" to (preferences[keyQuickReplay] ?: false),
            "isNotesTagsVisible" to (preferences[keyNotesTags] ?: false),
            "trafficFeedWidthDp" to (preferences[keyTrafficWidth] ?: 600f),
            "sidebarWidthDp" to (preferences[keySidebarWidth] ?: 260f),
            "bottomTrayHeightDp" to (preferences[keyTrayHeight] ?: 180f)
        )
    }

    public suspend fun saveSetting(key: String, value: Any) {
        dataStore.edit { preferences ->
            when (key) {
                "isTrafficFeedVisible" -> preferences[keyTrafficFeed] = value as Boolean
                "isInspectorVisible" -> preferences[keyInspector] = value as Boolean
                "isRulesConsoleVisible" -> preferences[keyRulesConsole] = value as Boolean
                "isQuickReplayVisible" -> preferences[keyQuickReplay] = value as Boolean
                "isNotesTagsVisible" -> preferences[keyNotesTags] = value as Boolean
                "trafficFeedWidthDp" -> preferences[keyTrafficWidth] = value as Float
                "sidebarWidthDp" -> preferences[keySidebarWidth] = value as Float
                "bottomTrayHeightDp" -> preferences[keyTrayHeight] = value as Float
            }
        }
    }
}
