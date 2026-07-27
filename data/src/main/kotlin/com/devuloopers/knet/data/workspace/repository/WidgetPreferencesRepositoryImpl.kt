package com.devuloopers.knet.data.workspace.repository

import com.devuloopers.knet.domain.workspace.model.WorkspaceLayoutSettings
import com.devuloopers.knet.domain.workspace.repository.WidgetPreferencesRepository
import com.devuloopers.knet.storage.WorkspacePreferencesDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Concrete implementation of [WidgetPreferencesRepository].
 * Maps raw Jetpack DataStore preferences to immutable [WorkspaceLayoutSettings] domain models.
 *
 * @property dataSource DataStore preference data source.
 */
class WidgetPreferencesRepositoryImpl(
    private val dataSource: WorkspacePreferencesDataSource
) : WidgetPreferencesRepository {

    override val settingsFlow: Flow<WorkspaceLayoutSettings> =
        dataSource.preferencesFlow.map { prefs ->
            WorkspaceLayoutSettings(
                isTrafficFeedVisible = prefs[WorkspacePreferencesDataSource.KEY_TRAFFIC_FEED_VISIBLE] ?: true,
                isInspectorVisible = prefs[WorkspacePreferencesDataSource.KEY_INSPECTOR_VISIBLE] ?: true,
                isRulesConsoleVisible = prefs[WorkspacePreferencesDataSource.KEY_RULES_CONSOLE_VISIBLE] ?: false,
                isQuickReplayVisible = prefs[WorkspacePreferencesDataSource.KEY_QUICK_REPLAY_VISIBLE] ?: false,
                isNotesTagsVisible = prefs[WorkspacePreferencesDataSource.KEY_NOTES_TAGS_VISIBLE] ?: false,
                trafficFeedWidthDp = prefs[WorkspacePreferencesDataSource.KEY_TRAFFIC_FEED_WIDTH] ?: 600f,
                sidebarWidthDp = prefs[WorkspacePreferencesDataSource.KEY_SIDEBAR_WIDTH] ?: 260f,
                bottomTrayHeightDp = prefs[WorkspacePreferencesDataSource.KEY_BOTTOM_TRAY_HEIGHT] ?: 180f
            )
        }

    override suspend fun saveSettings(settings: WorkspaceLayoutSettings) {
        dataSource.updateWidgetVisibilities(
            trafficFeedVisible = settings.isTrafficFeedVisible,
            inspectorVisible = settings.isInspectorVisible,
            rulesConsoleVisible = settings.isRulesConsoleVisible,
            quickReplayVisible = settings.isQuickReplayVisible,
            notesTagsVisible = settings.isNotesTagsVisible
        )
        dataSource.updatePanelDimensions(
            trafficFeedWidthDp = settings.trafficFeedWidthDp,
            sidebarWidthDp = settings.sidebarWidthDp,
            bottomTrayHeightDp = settings.bottomTrayHeightDp
        )
    }
}
