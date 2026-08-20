package com.devuloopers.knet.ui.desktop.settings

import com.devuloopers.knet.application.port.certificate.CertificateAuthorityStatus
import com.devuloopers.knet.application.port.certificate.CertificateAuthoritySummary
import com.devuloopers.knet.application.port.certificate.CertificateManagementPort
import com.devuloopers.knet.application.port.certificate.ClientCertificateSummary
import com.devuloopers.knet.application.port.certificate.MtlsRuleSpec
import com.devuloopers.knet.application.port.certificate.TrustInstallationResult
import com.devuloopers.knet.domain.settings.model.ApplicationSettings
import com.devuloopers.knet.domain.settings.model.ProxyPort
import com.devuloopers.knet.domain.settings.repository.ApplicationSettingsRepository
import com.devuloopers.knet.domain.settings.usecase.ObserveApplicationSettingsUseCase
import com.devuloopers.knet.domain.settings.usecase.UpdateApplicationSettingsUseCase
import com.devuloopers.knet.scripting.model.ScriptLanguage
import com.devuloopers.knet.ui.desktop.settings.model.SettingsField
import com.devuloopers.knet.ui.desktop.settings.model.SettingsIntent
import com.devuloopers.knet.ui.desktop.settings.model.SettingsNoticeTone
import com.devuloopers.knet.ui.desktop.settings.model.TimeoutUnit
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** Atomic in-memory application-settings repository used by Settings tests. */
private class FakeApplicationSettingsRepository(
    initialSettings: ApplicationSettings = ApplicationSettings(),
) : ApplicationSettingsRepository {
    val state = MutableStateFlow(initialSettings)
    var updateCount: Int = 0
    var failure: Exception? = null

    override val settings: Flow<ApplicationSettings> = state

    override suspend fun update(transform: (ApplicationSettings) -> ApplicationSettings) {
        failure?.let { throw it }
        updateCount += 1
        state.value = transform(state.value)
    }
}

/** Configurable certificate boundary used by Settings tests. */
private class FakeCertificateManager : CertificateManagementPort {
    var isTrusted: Boolean = false
    var installationResult: TrustInstallationResult = TrustInstallationResult.Installed

    override suspend fun isRootCertificateTrusted(): Boolean = isTrusted

    override suspend fun installRootCertificate(): TrustInstallationResult {
        if (installationResult is TrustInstallationResult.Installed) isTrusted = true
        return installationResult
    }

    override suspend fun authoritySummary(): CertificateAuthoritySummary = CertificateAuthoritySummary(
        CertificateAuthorityStatus.AVAILABLE,
        "CN=KNet Root CA",
        "CN=KNet Root CA",
        "01:23:45",
        "SHA256withRSA",
        "2024-01-01",
        "2034-01-01",
        "AA:BB:CC",
        "AA:BB:CC:DD",
        isTrusted,
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

/** In-memory desktop action boundary used by Settings tests. */
private class FakeSettingsPlatformActions : SettingsPlatformActions {
    override val dataDirectory: String = "/test/.knet"
    var openRequested: Boolean = false
    var openResult: Boolean = true

    override suspend fun openDataDirectory(): Boolean {
        openRequested = true
        return openResult
    }
}

/** Verifies validated drafts, atomic persistence, failure feedback, and Settings platform actions. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun `persisted application settings hydrate typed UI state`() = runTest(dispatcher) {
        val repository = FakeApplicationSettingsRepository(
            ApplicationSettings(
                proxyPort = ProxyPort(9090),
                defaultScriptLanguage = ScriptLanguage.KOTLIN,
                apiStudioTimeout = 5.minutes,
            ),
        )
        val viewModel = createViewModel(repository)

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("9090", viewModel.uiState.value.proxyPort)
        assertEquals(ScriptLanguage.KOTLIN, viewModel.uiState.value.scriptLanguage)
        assertEquals("5", viewModel.uiState.value.apiStudioTimeoutValue)
        assertEquals(TimeoutUnit.MINUTES, viewModel.uiState.value.apiStudioTimeoutUnit)
    }

    @Test
    fun `proxy port remains a draft until explicit valid commit`() = runTest(dispatcher) {
        val repository = FakeApplicationSettingsRepository()
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.processIntent(SettingsIntent.UpdateProxyPort("9090"))
        advanceUntilIdle()

        assertEquals(0, repository.updateCount)
        assertEquals(ProxyPort.Default, repository.state.value.proxyPort)
        assertTrue(SettingsField.PROXY_PORT in viewModel.uiState.value.dirtyFields)

        viewModel.processIntent(SettingsIntent.CommitProxyPort)
        advanceUntilIdle()

        assertEquals(1, repository.updateCount)
        assertEquals(ProxyPort(9090), repository.state.value.proxyPort)
        assertEquals(SettingsNoticeTone.SUCCESS, viewModel.uiState.value.notice?.tone)
    }

    @Test
    fun `invalid proxy port is rejected without persistence`() = runTest(dispatcher) {
        val repository = FakeApplicationSettingsRepository()
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.processIntent(SettingsIntent.UpdateProxyPort("99999"))
        viewModel.processIntent(SettingsIntent.CommitProxyPort)
        advanceUntilIdle()

        assertEquals(0, repository.updateCount)
        assertNotNull(viewModel.uiState.value.proxyPortError)
    }

    @Test
    fun `timeout draft commits as Kotlin duration`() = runTest(dispatcher) {
        val repository = FakeApplicationSettingsRepository()
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.processIntent(SettingsIntent.UpdateLiveInterceptionTimeout("2", TimeoutUnit.MINUTES))
        assertEquals(0, repository.updateCount)
        viewModel.processIntent(SettingsIntent.CommitLiveInterceptionTimeout)
        advanceUntilIdle()

        assertEquals(2.minutes, repository.state.value.liveInterceptionTimeout)
        assertNull(viewModel.uiState.value.liveInterceptionTimeoutError)
    }

    @Test
    fun `failed persistence is reported as error and never as success`() = runTest(dispatcher) {
        val repository = FakeApplicationSettingsRepository().apply {
            failure = IllegalStateException("disk unavailable")
        }
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.processIntent(SettingsIntent.UpdateProxyPort("9090"))
        viewModel.processIntent(SettingsIntent.CommitProxyPort)
        advanceUntilIdle()

        assertEquals(SettingsNoticeTone.ERROR, viewModel.uiState.value.notice?.tone)
        assertEquals("disk unavailable", viewModel.uiState.value.notice?.details)
        assertTrue(SettingsField.PROXY_PORT in viewModel.uiState.value.dirtyFields)
    }

    @Test
    fun `external updates refresh clean fields but preserve dirty drafts`() = runTest(dispatcher) {
        val repository = FakeApplicationSettingsRepository()
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.processIntent(SettingsIntent.UpdateProxyPort("9090"))
        repository.state.value = repository.state.value.copy(
            proxyPort = ProxyPort(8181),
            defaultScriptLanguage = ScriptLanguage.KOTLIN,
        )
        advanceUntilIdle()

        assertEquals("9090", viewModel.uiState.value.proxyPort)
        assertEquals(ScriptLanguage.KOTLIN, viewModel.uiState.value.scriptLanguage)
    }

    @Test
    fun `reset requires confirmation and restores only application defaults`() = runTest(dispatcher) {
        val repository = FakeApplicationSettingsRepository(
            ApplicationSettings(proxyPort = ProxyPort(9090), apiStudioTimeout = 5.minutes),
        )
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.processIntent(SettingsIntent.RequestResetDefaults)
        assertTrue(viewModel.uiState.value.isResetConfirmationVisible)
        assertEquals(0, repository.updateCount)

        viewModel.processIntent(SettingsIntent.ConfirmResetDefaults)
        advanceUntilIdle()

        assertEquals(ApplicationSettings(), repository.state.value)
        assertFalse(viewModel.uiState.value.isResetConfirmationVisible)
    }

    @Test
    fun `manual trust result exposes full instructions as warning details`() = runTest(dispatcher) {
        val certificates = FakeCertificateManager().apply {
            installationResult = TrustInstallationResult.ManualActionRequired(
                message = "Administrator approval is required.",
                instructions = "Open the system trust settings and approve KNet Root CA.",
            )
        }
        val viewModel = createViewModel(certificates = certificates)
        advanceUntilIdle()

        viewModel.processIntent(SettingsIntent.InstallRootCa)
        advanceUntilIdle()

        assertEquals(SettingsNoticeTone.WARNING, viewModel.uiState.value.notice?.tone)
        assertEquals(
            "Open the system trust settings and approve KNet Root CA.",
            viewModel.uiState.value.notice?.details,
        )
    }

    @Test
    fun `open data directory delegates to desktop platform boundary`() = runTest(dispatcher) {
        val platform = FakeSettingsPlatformActions()
        val viewModel = createViewModel(platformActions = platform)
        advanceUntilIdle()

        viewModel.processIntent(SettingsIntent.OpenDataDirectory)
        advanceUntilIdle()

        assertTrue(platform.openRequested)
        assertEquals(platform.dataDirectory, viewModel.uiState.value.dataDirectory)
    }

    private fun createViewModel(
        repository: FakeApplicationSettingsRepository = FakeApplicationSettingsRepository(),
        certificates: FakeCertificateManager = FakeCertificateManager(),
        platformActions: FakeSettingsPlatformActions = FakeSettingsPlatformActions(),
    ): SettingsViewModel = SettingsViewModel(
        observeApplicationSettings = ObserveApplicationSettingsUseCase(repository),
        updateApplicationSettings = UpdateApplicationSettingsUseCase(repository),
        certificateManager = certificates,
        platformActions = platformActions,
        ioDispatcher = dispatcher,
    )
}
