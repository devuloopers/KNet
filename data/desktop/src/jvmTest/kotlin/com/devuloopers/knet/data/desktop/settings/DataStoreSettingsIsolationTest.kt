package com.devuloopers.knet.data.desktop.settings

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.devuloopers.knet.data.desktop.workspace.repository.WidgetPreferencesRepositoryImpl
import com.devuloopers.knet.domain.settings.model.ProxyPort
import com.devuloopers.knet.domain.workspace.model.TrafficTableColumnWidths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

/** Verifies that application preferences and workspace layout have independent atomic ownership. */
@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreSettingsIsolationTest {

    @Test
    fun `scrolling defaults to smart and manual preference survives reopening and unrelated changes`() = runTest {
        val directory = createTempDirectory("knet-scroll-preferences")
        var scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        fun store() = PreferenceDataStoreFactory.createWithPath(scope = scope,
            produceFile = { directory.resolve("settings.preferences_pb").toString().toPath() })
        var dataStore = store()
        try {
            var repository = WidgetPreferencesRepositoryImpl(dataStore)
            assertEquals(com.devuloopers.knet.domain.workspace.model.TrafficAutoScrollSettings(), repository.settingsFlow.first().trafficAutoScroll)
            val manual = com.devuloopers.knet.domain.workspace.model.TrafficAutoScrollSettings(
                com.devuloopers.knet.domain.workspace.model.TrafficAutoScrollMode.MANUAL, false)
            repository.updateSettings { it.copy(trafficAutoScroll = manual) }
            repository.updateSettings { it.copy(trafficFeedWidthDp = 730f) }
            assertEquals(manual, repository.settingsFlow.first().trafficAutoScroll)
            scope.coroutineContext[kotlinx.coroutines.Job]!!.cancel()
            scope.coroutineContext[kotlinx.coroutines.Job]!!.join()
            scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
            dataStore = store()
            repository = WidgetPreferencesRepositoryImpl(dataStore)
            assertEquals(manual, repository.settingsFlow.first().trafficAutoScroll)
            assertEquals(730f, repository.settingsFlow.first().trafficFeedWidthDp)
            dataStore.edit { it[stringPreferencesKey("traffic_auto_scroll_mode")] = "FUTURE_UNKNOWN" }
            assertEquals(com.devuloopers.knet.domain.workspace.model.TrafficAutoScrollMode.SMART,
                repository.settingsFlow.first().trafficAutoScroll.mode)
            assertEquals(false, repository.settingsFlow.first().trafficAutoScroll.manualEnabled)
        } finally { scope.cancel(); directory.toFile().deleteRecursively() }
    }

    @Test
    fun `concurrent application and workspace updates preserve both setting groups`() = runTest {
        val directory = createTempDirectory("knet-settings-test")
        val dataStoreScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val dataStore = PreferenceDataStoreFactory.createWithPath(
            scope = dataStoreScope,
            produceFile = {
                directory.resolve("settings.preferences_pb").toString().toPath()
            },
        )
        val applicationRepository = DataStoreApplicationSettingsRepository(dataStore)
        val workspaceRepository = WidgetPreferencesRepositoryImpl(dataStore)

        try {
            listOf(
                async {
                    applicationRepository.update { current ->
                        current.copy(
                            proxyPort = ProxyPort(9090),
                            apiStudioTimeout = 5.minutes,
                        )
                    }
                },
                async {
                    workspaceRepository.updateSettings { current ->
                        current.copy(
                            trafficFeedWidthDp = 720f,
                            activeSessionId = "draft-42",
                        )
                    }
                },
            ).awaitAll()

            assertEquals(ProxyPort(9090), applicationRepository.settings.first().proxyPort)
            assertEquals(5.minutes, applicationRepository.settings.first().apiStudioTimeout)
            assertEquals(720f, workspaceRepository.settingsFlow.first().trafficFeedWidthDp)
            assertEquals("draft-42", workspaceRepository.settingsFlow.first().activeSessionId)

            workspaceRepository.updateSettings { current ->
                current.copy(isInspectorVisible = false)
            }
            applicationRepository.update { current ->
                current.copy(autoClearTrafficOnStartup = true)
            }

            assertEquals(ProxyPort(9090), applicationRepository.settings.first().proxyPort)
            assertEquals(720f, workspaceRepository.settingsFlow.first().trafficFeedWidthDp)
            assertEquals(false, workspaceRepository.settingsFlow.first().isInspectorVisible)

            val resizedColumns = TrafficTableColumnWidths(
                hostDp = 312f,
                pathDp = 480f,
            )
            workspaceRepository.updateSettings { current ->
                current.copy(trafficTableColumnWidths = resizedColumns)
            }
            assertEquals(
                resizedColumns,
                workspaceRepository.settingsFlow.first().trafficTableColumnWidths,
            )

            workspaceRepository.updateSettings { current ->
                current.copy(
                    trafficTableColumnWidths = current.trafficTableColumnWidths.copy(pathDp = null),
                )
            }
            assertNull(workspaceRepository.settingsFlow.first().trafficTableColumnWidths.pathDp)
        } finally {
            dataStoreScope.cancel()
            directory.toFile().deleteRecursively()
        }
    }
}
