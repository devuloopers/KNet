package com.devuloopers.knet.ui.desktop.apistudio.viewmodel

import com.devuloopers.knet.domain.workspace.model.WorkspaceLayoutSettings
import com.devuloopers.knet.domain.workspace.repository.WidgetPreferencesRepository
import com.devuloopers.knet.domain.workspace.usecase.GetWorkspaceLayoutUseCase
import com.devuloopers.knet.domain.workspace.usecase.SaveWorkspaceLayoutUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ApiStudioWorkspaceCoordinatorTest {

    @Test
    fun `workspace updates are persisted in intent order`() = runTest {
        val repository = RecordingWorkspaceRepository()
        val coordinator = ApiStudioWorkspaceCoordinator(
            scope = backgroundScope,
            dispatcher = StandardTestDispatcher(testScheduler),
            getWorkspaceLayout = GetWorkspaceLayoutUseCase(repository),
            saveWorkspaceLayout = SaveWorkspaceLayoutUseCase(repository),
            onFailure = { throw it }
        )

        coordinator.schedule { it.copy(activeSessionId = "draft:first") }
        coordinator.schedule { it.copy(activeSessionId = "draft:second") }
        val completion = async {
            coordinator.updateAndAwait { it.copy(activeSessionId = "saved:final") }
        }
        advanceUntilIdle()

        assertTrue(completion.await().isSuccess)
        assertEquals(
            listOf("draft:first", "draft:second", "saved:final"),
            repository.saved.map(WorkspaceLayoutSettings::activeSessionId)
        )
        assertEquals("saved:final", repository.settings.value.activeSessionId)
    }

    private class RecordingWorkspaceRepository : WidgetPreferencesRepository {
        val settings = MutableStateFlow(WorkspaceLayoutSettings())
        val saved = mutableListOf<WorkspaceLayoutSettings>()

        override val settingsFlow: Flow<WorkspaceLayoutSettings> = settings

        override suspend fun saveSettings(settings: WorkspaceLayoutSettings) {
            saved += settings
            this.settings.value = settings
        }
    }
}
