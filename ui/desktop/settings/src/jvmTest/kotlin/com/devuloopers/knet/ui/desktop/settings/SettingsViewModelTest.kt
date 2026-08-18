package com.devuloopers.knet.ui.desktop.settings

import com.devuloopers.knet.domain.workspace.model.TimeoutUnit
import com.devuloopers.knet.domain.workspace.model.WorkspaceLayoutSettings
import com.devuloopers.knet.domain.workspace.repository.WidgetPreferencesRepository
import com.devuloopers.knet.application.port.certificate.CertificateAuthoritySummary
import com.devuloopers.knet.application.port.certificate.CertificateManagementPort
import com.devuloopers.knet.application.port.certificate.ClientCertificateSummary
import com.devuloopers.knet.application.port.certificate.MtlsRuleSpec
import com.devuloopers.knet.ui.desktop.settings.model.SettingsIntent
import com.devuloopers.knet.ui.desktop.settings.model.SettingsState
import com.devuloopers.knet.ui.desktop.settings.model.SettingsTab
import com.devuloopers.knet.ui.desktop.settings.platform.SettingsPlatformActions
import com.devuloopers.knet.ui.desktop.settings.viewmodel.SettingsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fake implementation of [WidgetPreferencesRepository] for unit testing.
 */
private class FakeWidgetPreferencesRepository(
    initialSettings: WorkspaceLayoutSettings = WorkspaceLayoutSettings()
) : WidgetPreferencesRepository {
    val stateFlow = MutableStateFlow(initialSettings)
    override val settingsFlow: Flow<WorkspaceLayoutSettings> = stateFlow

    override suspend fun saveSettings(settings: WorkspaceLayoutSettings) {
        stateFlow.value = settings
    }
}

/**
 * Fake implementation of [CertificateManagementPort] for unit testing.
 */
private class FakeCertificateManager : CertificateManagementPort {
    var isTrusted: Boolean = false
    override suspend fun isRootCertificateTrusted(): Boolean = isTrusted
    override suspend fun installRootCertificate(): Boolean {
        isTrusted = true
        return true
    }
    override suspend fun authoritySummary(): CertificateAuthoritySummary = CertificateAuthoritySummary(
        "AVAILABLE", "CN=KNet Root CA", "CN=KNet Root CA", "01:23:45", "SHA256withRSA",
        "2024-01-01", "2034-01-01", "AA:BB:CC", "AA:BB:CC:DD", isTrusted,
    )
    override suspend fun clientCertificates(): List<ClientCertificateSummary> = emptyList()
    override suspend fun importClientCertificate(path: String, alias: String, passphrase: String) = Unit
    override suspend fun exportClientCertificate(alias: String, destinationPath: String) = Unit
    override suspend fun deleteClientCertificate(alias: String) = Unit
    override suspend fun setClientCertificateEnabled(alias: String, enabled: Boolean) = Unit
    override suspend fun mtlsRules(): List<MtlsRuleSpec> = emptyList()
    override suspend fun addMtlsRule(rule: MtlsRuleSpec) = Unit
    override suspend fun editMtlsRule(rule: MtlsRuleSpec) = Unit
    override suspend fun deleteMtlsRule(ruleName: String) = Unit
}

/** In-memory desktop action boundary used by settings ViewModel tests. */
private class FakeSettingsPlatformActions : SettingsPlatformActions {
    override val dataDirectory: String = "/test/.knet"
    var openRequested: Boolean = false

    override suspend fun openDataDirectory(): Boolean {
        openRequested = true
        return true
    }
}

/**
 * Unit tests verifying Settings state transitions, timeout conversions, and intent contracts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @kotlin.test.BeforeTest
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(testDispatcher)
    }

    @kotlin.test.AfterTest
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun `verify default settings state values`() {
        val state = SettingsState()
        assertEquals(SettingsTab.NETWORK_PROXY, state.activeTab)
        assertEquals("8080", state.proxyPort)
        assertFalse(state.isCaTrusted)
        assertFalse(state.autoClearTrafficOnStartup)
        assertEquals(10, state.maxPayloadMb)
        assertEquals("DARK", state.theme)
        assertEquals("JAVASCRIPT", state.scriptLanguage)
        assertEquals("60", state.apiStudioTimeoutValue)
        assertEquals(TimeoutUnit.SECONDS, state.apiStudioTimeoutUnit)
        assertEquals("60", state.liveInterceptionTimeoutValue)
        assertEquals(TimeoutUnit.SECONDS, state.liveInterceptionTimeoutUnit)
    }

    @Test
    fun `verify timeout unit toSeconds and fromSeconds conversions`() {
        assertEquals(45, TimeoutUnit.SECONDS.toSeconds(45))
        assertEquals(120, TimeoutUnit.MINUTES.toSeconds(2))

        val (val1, unit1) = TimeoutUnit.fromSeconds(120)
        assertEquals(2, val1)
        assertEquals(TimeoutUnit.MINUTES, unit1)

        val (val2, unit2) = TimeoutUnit.fromSeconds(45)
        assertEquals(45, val2)
        assertEquals(TimeoutUnit.SECONDS, unit2)
    }

    @Test
    fun `verify UpdateApiStudioTimeout persists minutes converted to total seconds`() = runTest(testDispatcher) {
        val repo = FakeWidgetPreferencesRepository()
        val certManager = FakeCertificateManager()
        val viewModel = SettingsViewModel(repo, certManager, FakeSettingsPlatformActions(), testDispatcher)

        advanceUntilIdle()

        // Set API Studio timeout to 3 minutes
        viewModel.processIntent(SettingsIntent.UpdateApiStudioTimeout("3", TimeoutUnit.MINUTES))
        advanceUntilIdle()

        assertEquals("3", viewModel.uiState.value.apiStudioTimeoutValue)
        assertEquals(TimeoutUnit.MINUTES, viewModel.uiState.value.apiStudioTimeoutUnit)
        assertEquals(180, repo.stateFlow.value.apiStudioTimeoutSeconds)
    }

    @Test
    fun `verify UpdateLiveInterceptionTimeout persists application breakpoint deadline`() = runTest(testDispatcher) {
        val repo = FakeWidgetPreferencesRepository()
        val certManager = FakeCertificateManager()
        val viewModel = SettingsViewModel(repo, certManager, FakeSettingsPlatformActions(), testDispatcher)

        advanceUntilIdle()

        // Set Live Interception timeout to 2 minutes (120 sec)
        viewModel.processIntent(SettingsIntent.UpdateLiveInterceptionTimeout("2", TimeoutUnit.MINUTES))
        advanceUntilIdle()

        assertEquals("2", viewModel.uiState.value.liveInterceptionTimeoutValue)
        assertEquals(TimeoutUnit.MINUTES, viewModel.uiState.value.liveInterceptionTimeoutUnit)
        assertEquals(120, repo.stateFlow.value.liveInterceptionTimeoutSeconds)
    }

    @Test
    fun `verify ResetDefaults restores default timeouts to 60s for both`() = runTest(testDispatcher) {
        val customInitial = WorkspaceLayoutSettings(
            apiStudioTimeoutSeconds = 300,
            liveInterceptionTimeoutSeconds = 600
        )
        val repo = FakeWidgetPreferencesRepository(customInitial)
        val certManager = FakeCertificateManager()
        val viewModel = SettingsViewModel(repo, certManager, FakeSettingsPlatformActions(), testDispatcher)

        advanceUntilIdle()

        assertEquals("5", viewModel.uiState.value.apiStudioTimeoutValue)
        assertEquals(TimeoutUnit.MINUTES, viewModel.uiState.value.apiStudioTimeoutUnit)

        // Reset defaults
        viewModel.processIntent(SettingsIntent.ResetDefaults)
        advanceUntilIdle()

        assertEquals("1", viewModel.uiState.value.apiStudioTimeoutValue)
        assertEquals(TimeoutUnit.MINUTES, viewModel.uiState.value.apiStudioTimeoutUnit)
        assertEquals("1", viewModel.uiState.value.liveInterceptionTimeoutValue)
        assertEquals(TimeoutUnit.MINUTES, viewModel.uiState.value.liveInterceptionTimeoutUnit)
        assertEquals(60, repo.stateFlow.value.apiStudioTimeoutSeconds)
        assertEquals(60, repo.stateFlow.value.liveInterceptionTimeoutSeconds)
    }

    @Test
    fun `OpenDataDirectory delegates to the injected desktop platform action`() = runTest(testDispatcher) {
        val platformActions = FakeSettingsPlatformActions()
        val viewModel = SettingsViewModel(
            FakeWidgetPreferencesRepository(),
            FakeCertificateManager(),
            platformActions,
            testDispatcher,
        )
        advanceUntilIdle()

        viewModel.processIntent(SettingsIntent.OpenDataDirectory)
        advanceUntilIdle()

        assertTrue(platformActions.openRequested)
        assertEquals(platformActions.dataDirectory, viewModel.uiState.value.dataDirectory)
    }
}
