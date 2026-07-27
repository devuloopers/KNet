package com.devuloopers.knet.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.File

/**
 * Data source managing low-level Jetpack DataStore Preferences for workspace layout.
 * Persists widget visibilities and panel sizes to disk (`workspace_preferences.preferences_pb`).
 *
 * @param baseDir The root application data directory.
 */
class WorkspacePreferencesDataSource(baseDir: File) {

    private val dataStoreFile = File(baseDir.apply { mkdirs() }, "workspace_preferences.preferences_pb")

    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { dataStoreFile }
    )

    companion object {
        val KEY_TRAFFIC_FEED_VISIBLE = booleanPreferencesKey("widget_traffic_feed_visible")
        val KEY_INSPECTOR_VISIBLE = booleanPreferencesKey("widget_inspector_visible")
        val KEY_RULES_CONSOLE_VISIBLE = booleanPreferencesKey("widget_rules_console_visible")
        val KEY_QUICK_REPLAY_VISIBLE = booleanPreferencesKey("widget_quick_replay_visible")
        val KEY_NOTES_TAGS_VISIBLE = booleanPreferencesKey("widget_notes_tags_visible")

        val KEY_TRAFFIC_FEED_WIDTH = floatPreferencesKey("dim_traffic_feed_width")
        val KEY_SIDEBAR_WIDTH = floatPreferencesKey("dim_sidebar_width")
        val KEY_BOTTOM_TRAY_HEIGHT = floatPreferencesKey("dim_bottom_tray_height")
    }

    /** Stream of raw preference data map with safe error handling fallback. */
    val preferencesFlow: Flow<Preferences> = dataStore.data
        .catch { exception ->
            if (exception is java.io.IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }

    /**
     * Updates widget visibility preference booleans.
     */
    suspend fun updateWidgetVisibilities(
        trafficFeedVisible: Boolean,
        inspectorVisible: Boolean,
        rulesConsoleVisible: Boolean,
        quickReplayVisible: Boolean,
        notesTagsVisible: Boolean
    ) {
        dataStore.edit { prefs ->
            prefs[KEY_TRAFFIC_FEED_VISIBLE] = trafficFeedVisible
            prefs[KEY_INSPECTOR_VISIBLE] = inspectorVisible
            prefs[KEY_RULES_CONSOLE_VISIBLE] = rulesConsoleVisible
            prefs[KEY_QUICK_REPLAY_VISIBLE] = quickReplayVisible
            prefs[KEY_NOTES_TAGS_VISIBLE] = notesTagsVisible
        }
    }

    /**
     * Updates panel dimension pixel values.
     */
    suspend fun updatePanelDimensions(
        trafficFeedWidthDp: Float,
        sidebarWidthDp: Float,
        bottomTrayHeightDp: Float
    ) {
        dataStore.edit { prefs ->
            prefs[KEY_TRAFFIC_FEED_WIDTH] = trafficFeedWidthDp
            prefs[KEY_SIDEBAR_WIDTH] = sidebarWidthDp
            prefs[KEY_BOTTOM_TRAY_HEIGHT] = bottomTrayHeightDp
        }
    }
}
