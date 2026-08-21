package com.devuloopers.knet.ui.desktop.traffic

import com.devuloopers.knet.domain.workspace.model.TrafficTableColumnWidths
import com.devuloopers.knet.domain.workspace.model.WorkspaceLayoutSettings
import com.devuloopers.knet.domain.workspace.repository.WidgetPreferencesRepository
import com.devuloopers.knet.ui.desktop.traffic.model.TrafficColumn
import com.devuloopers.knet.ui.desktop.traffic.model.TrafficIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class TrafficColumnWidthViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `resize is immediate and persistence occurs when drag commits`() = runTest(dispatcher) {
        val repository = RecordingWorkspaceRepository(
            WorkspaceLayoutSettings(
                trafficTableColumnWidths = TrafficTableColumnWidths(hostDp = 240f),
            ),
        )
        val viewModel = FakeTrafficViewModelFactory.create(
            customWorkspacePreferencesRepository = repository,
        )
        advanceUntilIdle()

        assertEquals(240f, viewModel.uiState.value.columnWidths.hostDp)

        viewModel.processIntent(TrafficIntent.ResizeColumn(TrafficColumn.HOST, 360f))

        assertEquals(360f, viewModel.uiState.value.columnWidths.hostDp)
        assertEquals(240f, repository.current.trafficTableColumnWidths.hostDp)

        viewModel.processIntent(TrafficIntent.CommitColumnWidths)
        advanceUntilIdle()

        assertEquals(360f, repository.current.trafficTableColumnWidths.hostDp)
    }

    @Test
    fun `single and full resets persist their default modes`() = runTest(dispatcher) {
        val repository = RecordingWorkspaceRepository(
            WorkspaceLayoutSettings(
                trafficTableColumnWidths = TrafficTableColumnWidths(hostDp = 360f, pathDp = 520f),
            ),
        )
        val viewModel = FakeTrafficViewModelFactory.create(
            customWorkspacePreferencesRepository = repository,
        )
        advanceUntilIdle()

        viewModel.processIntent(TrafficIntent.ResetColumnWidth(TrafficColumn.PATH))
        advanceUntilIdle()

        assertNull(repository.current.trafficTableColumnWidths.pathDp)
        assertEquals(360f, repository.current.trafficTableColumnWidths.hostDp)

        viewModel.processIntent(TrafficIntent.ResetColumnWidths)
        advanceUntilIdle()

        assertEquals(TrafficTableColumnWidths(), repository.current.trafficTableColumnWidths)
    }
}

private class RecordingWorkspaceRepository(
    initial: WorkspaceLayoutSettings,
) : WidgetPreferencesRepository {
    private val settings = MutableStateFlow(initial)

    override val settingsFlow: Flow<WorkspaceLayoutSettings> = settings

    val current: WorkspaceLayoutSettings
        get() = settings.value

    override suspend fun updateSettings(
        transform: (WorkspaceLayoutSettings) -> WorkspaceLayoutSettings,
    ) {
        settings.value = transform(settings.value)
    }
}
