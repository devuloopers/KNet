package com.devuloopers.knet.ui.desktop.settings

import com.devuloopers.knet.domain.workspace.model.TimeoutUnit
import com.devuloopers.knet.domain.workspace.model.WorkspaceLayoutSettings
import com.devuloopers.knet.domain.workspace.repository.WidgetPreferencesRepository
import com.devuloopers.knet.engine.certificate.CertificateManager
import com.devuloopers.knet.ui.desktop.settings.model.SettingsIntent
import com.devuloopers.knet.ui.desktop.settings.model.SettingsState
import com.devuloopers.knet.ui.desktop.settings.model.SettingsTab
import com.devuloopers.knet.ui.desktop.settings.viewmodel.SettingsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.io.File
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
 * Fake implementation of [CertificateManager] for unit testing.
 */
private class FakeCertificateManager : CertificateManager {
    var isTrusted: Boolean = false
    override fun isCaTrustedByOs(): Boolean = isTrusted
    override fun installRootCertificate(): Boolean {
        isTrusted = true
        return true
    }
    override fun getCaStatus(): String = "AVAILABLE"
    override fun getCaSubject(): String = "CN=KNet Root CA"
    override fun getCaIssuer(): String = "CN=KNet Root CA"
    override fun getCaSerialNumber(): String = "01:23:45"
    override fun getCaSignatureAlgorithm(): String = "SHA256withRSA"
    override fun getCaValidFrom(): String = "2024-01-01"
    override fun getCaValidUntil(): String = "2034-01-01"
    override fun getCaSha1Fingerprint(): String = "AA:BB:CC"
    override fun getCaSha256Fingerprint(): String = "AA:BB:CC:DD"
    override fun getClientCertificates(): List<com.devuloopers.knet.engine.certificate.EngineClientCertificate> = emptyList()
    override fun importClientCertificate(path: String, alias: String, passphrase: String) {}
    override fun exportClientCertificate(alias: String, destinationPath: String) {}
    override fun deleteClientCertificate(alias: String) {}
    override fun toggleCertificateEnabled(alias: String, enabled: Boolean) {}
    override fun getMtlsRules(): List<com.devuloopers.knet.engine.certificate.EngineMtlsRule> = emptyList()
    override fun addMtlsRule(rule: com.devuloopers.knet.engine.certificate.EngineMtlsRule) {}
    override fun editMtlsRule(rule: com.devuloopers.knet.engine.certificate.EngineMtlsRule) {}
    override fun deleteMtlsRule(ruleName: String) {}
    override fun getKeyManagerFactory(host: String): javax.net.ssl.KeyManagerFactory? = null
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
        val viewModel = SettingsViewModel(repo, certManager, testDispatcher)

        advanceUntilIdle()

        // Set API Studio timeout to 3 minutes
        viewModel.processIntent(SettingsIntent.UpdateApiStudioTimeout("3", TimeoutUnit.MINUTES))
        advanceUntilIdle()

        assertEquals("3", viewModel.uiState.value.apiStudioTimeoutValue)
        assertEquals(TimeoutUnit.MINUTES, viewModel.uiState.value.apiStudioTimeoutUnit)
        assertEquals(180, repo.stateFlow.value.apiStudioTimeoutSeconds)
    }

    @Test
    fun `verify UpdateLiveInterceptionTimeout persists seconds and synchronizes InterceptCoordinator`() = runTest(testDispatcher) {
        val repo = FakeWidgetPreferencesRepository()
        val certManager = FakeCertificateManager()
        val viewModel = SettingsViewModel(repo, certManager, testDispatcher)

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
        val viewModel = SettingsViewModel(repo, certManager, testDispatcher)

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
}
