package com.devuloopers.knet.data.workspace

import com.devuloopers.knet.data.workspace.repository.WidgetPreferencesRepositoryImpl
import com.devuloopers.knet.domain.workspace.model.WorkspaceLayoutSettings
import com.devuloopers.knet.domain.workspace.usecase.GetWorkspaceLayoutUseCase
import com.devuloopers.knet.domain.workspace.usecase.SaveWorkspaceLayoutUseCase
import com.devuloopers.knet.storage.WorkspacePreferencesDataSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkspacePreferencesTest {

    @Test
    fun testDataStorePersistenceSaveAndRestore() = runBlocking {
        val tempDir = File.createTempFile("knet_test_prefs", "").apply {
            delete()
            mkdirs()
        }

        try {
            val dataSource = WorkspacePreferencesDataSource(tempDir)
            val repository = WidgetPreferencesRepositoryImpl(dataSource)
            val getUseCase = GetWorkspaceLayoutUseCase(repository)
            val saveUseCase = SaveWorkspaceLayoutUseCase(repository)

            // 1. Verify default values
            val initialSettings = getUseCase.execute().first()
            assertTrue(initialSettings.isTrafficFeedVisible)
            assertTrue(initialSettings.isInspectorVisible)
            assertFalse(initialSettings.isRulesConsoleVisible)
            assertFalse(initialSettings.isQuickReplayVisible)
            assertFalse(initialSettings.isNotesTagsVisible)
            assertEquals(600f, initialSettings.trafficFeedWidthDp)

            // 2. Persist updated layout settings
            val updated = WorkspaceLayoutSettings(
                isTrafficFeedVisible = true,
                isInspectorVisible = true,
                isRulesConsoleVisible = true,
                isQuickReplayVisible = true,
                isNotesTagsVisible = true,
                trafficFeedWidthDp = 750f,
                sidebarWidthDp = 300f,
                bottomTrayHeightDp = 220f
            )
            saveUseCase.execute(updated)

            // 3. Read back from DataStore flow
            val restored = getUseCase.execute().first()
            assertTrue(restored.isRulesConsoleVisible)
            assertTrue(restored.isQuickReplayVisible)
            assertTrue(restored.isNotesTagsVisible)
            assertEquals(750f, restored.trafficFeedWidthDp)
            assertEquals(300f, restored.sidebarWidthDp)
            assertEquals(220f, restored.bottomTrayHeightDp)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testFallbackToDefaultsOnCorruptedFile() = runBlocking {
        val tempDir = File.createTempFile("knet_corrupt_test", "").apply {
            delete()
            mkdirs()
        }

        try {
            // Write corrupt non-protobuf data to preferences file
            val prefFile = File(tempDir, "workspace_preferences.preferences_pb")
            prefFile.writeText("INVALID_CORRUPTED_BYTES_HERE")

            val dataSource = WorkspacePreferencesDataSource(tempDir)
            val repository = WidgetPreferencesRepositoryImpl(dataSource)
            val getUseCase = GetWorkspaceLayoutUseCase(repository)

            // Should safely fallback to default settings without crashing
            val fallbackSettings = getUseCase.execute().first()
            assertTrue(fallbackSettings.isTrafficFeedVisible)
            assertTrue(fallbackSettings.isInspectorVisible)
            assertEquals(600f, fallbackSettings.trafficFeedWidthDp)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
