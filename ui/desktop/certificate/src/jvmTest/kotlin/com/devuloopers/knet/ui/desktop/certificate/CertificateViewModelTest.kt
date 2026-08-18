package com.devuloopers.knet.ui.desktop.certificate

import com.devuloopers.knet.application.port.certificate.CertificateAuthoritySummary
import com.devuloopers.knet.application.port.certificate.CertificateManagementPort
import com.devuloopers.knet.application.port.certificate.ClientCertificateSummary
import com.devuloopers.knet.application.port.certificate.ClientCertificateFormat
import com.devuloopers.knet.application.port.certificate.MtlsRuleSpec
import com.devuloopers.knet.ui.desktop.certificate.model.CaStatus
import com.devuloopers.knet.ui.desktop.certificate.model.CertificateIntent
import com.devuloopers.knet.ui.desktop.certificate.model.TrustInstallationState
import com.devuloopers.knet.ui.desktop.certificate.viewmodel.CertificateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

/**
 * Fake implementation of [CertificateManagementPort] to enable unit testing the UI presentation layer.
 */
class FakeCertificateManager : CertificateManagementPort {
    private val clientCertificates: MutableList<ClientCertificateSummary> = mutableListOf()
    private val mtlsRules: MutableList<MtlsRuleSpec> = mutableListOf()

    override suspend fun authoritySummary(): CertificateAuthoritySummary = CertificateAuthoritySummary(
        "AVAILABLE",
        "CN=KNet Intercepting Root CA, O=Devuloopers, L=Desktop",
        "CN=KNet Intercepting Root CA, O=Devuloopers, L=Desktop",
        "DE:AD:BE:EF:12:34:56:78",
        "SHA256withRSA",
        "2026-08-01",
        "2036-08-01",
        "7A:B2:D5:E8:C2:59:71:0F:7D:F8:8C:BE:1C:2B:E9:9A:8F:B1:01:C2",
        "F5:C2:17:8D:2D:E8:C9:F0:A1:2B:3C:4D:5E:6F:7A:8B:9C:0D:1E:2F:3A:4B:5C:6D:7E:8F:90:A1:B2:C3",
        true,
    )
    override suspend fun installRootCertificate(): Boolean = true
    override suspend fun isRootCertificateTrusted(): Boolean = true
    override suspend fun clientCertificates(): List<ClientCertificateSummary> = clientCertificates.toList()

    override suspend fun importClientCertificate(path: String, alias: String, passphrase: String) {
        clientCertificates.add(
            ClientCertificateSummary(
                alias = alias,
                subject = "CN=$alias, O=Client, L=Local",
                host = "*",
                expiration = "2029-12-31",
                enabled = true,
                format = ClientCertificateFormat.PKCS12,
                daysUntilExpiration = 365,
                subjectDn = "",
                issuerDn = "",
                serialNumber = "",
                sanList = emptyList(),
                publicKeyAlgorithm = "RSA",
                sha256Fingerprint = "",
            )
        )
    }

    override suspend fun exportClientCertificate(alias: String, destinationPath: String) {}

    override suspend fun deleteClientCertificate(alias: String) {
        clientCertificates.removeAll { it.alias == alias }
    }

    override suspend fun setClientCertificateEnabled(alias: String, enabled: Boolean) {
        val index = clientCertificates.indexOfFirst { it.alias == alias }
        if (index != -1) {
            val existing = clientCertificates[index]
            clientCertificates[index] = existing.copy(enabled = enabled)
        }
    }

    override suspend fun mtlsRules(): List<MtlsRuleSpec> = mtlsRules.toList()

    override suspend fun addMtlsRule(rule: MtlsRuleSpec) {
        mtlsRules.add(rule)
    }

    override suspend fun editMtlsRule(rule: MtlsRuleSpec) {
        val index = mtlsRules.indexOfFirst { it.ruleName == rule.ruleName }
        if (index != -1) {
            mtlsRules[index] = rule
        }
    }

    override suspend fun deleteMtlsRule(ruleName: String) {
        mtlsRules.removeAll { it.ruleName == ruleName }
    }
}

/**
 * Unit tests verifying intent processing and state updates in [CertificateViewModel]
 * utilizing [FakeCertificateManager].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CertificateViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var certificateManager: FakeCertificateManager
    private lateinit var viewModel: CertificateViewModel

    /**
     * Set up routine running before each test execution to register the main dispatcher.
     */
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        certificateManager = FakeCertificateManager()
        viewModel = CertificateViewModel(certificateManager, testDispatcher)
    }

    /**
     * Teardown routine running after each test execution to reset the main dispatcher.
     */
    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Verifies that the ViewModel loads CA details successfully upon initialization.
     *
     * Design Intent: UI screens display correct status and subject credentials immediately when loaded.
     */
    @Test
    fun testInitialState() = runTest {
        val state = viewModel.uiState.value
        assertEquals(CaStatus.AVAILABLE, state.caStatus)
        assertEquals("CN=KNet Intercepting Root CA, O=Devuloopers, L=Desktop", state.caDetails.subject)
    }

    /**
     * Verifies that the refresh intent reloads CA details.
     *
     * Design Intent: Responds to refresh buttons in the dashboard toolbar.
     */
    @Test
    fun testRefreshIntent() = runTest {
        viewModel.processIntent(CertificateIntent.Refresh)
        val state = viewModel.uiState.value
        assertEquals(CaStatus.AVAILABLE, state.caStatus)
    }

    /**
     * Verifies the trust installation wizard state transitions.
     *
     * Design Intent: Wizard should move through status transitions correctly (Installing -> Installed).
     */
    @Test
    fun testInstallTrustIntent() = runTest {
        viewModel.processIntent(CertificateIntent.InstallTrust)
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(TrustInstallationState.INSTALLED, state.trustState)
    }

    /**
     * Verifies client certificate import and deletion flow.
     *
     * Design Intent: Tests import dialog submission and rule removal logic.
     */
    @Test
    fun testImportAndDeleteCertificateIntents() = runTest {
        // Import
        viewModel.processIntent(CertificateIntent.ImportCertificate(path = "/path/to/cert", alias = "test-alias"))
        advanceUntilIdle()
        var state = viewModel.uiState.value
        assertEquals(1, state.clientCertificates.size)
        assertEquals("test-alias", state.clientCertificates[0].alias)
        assertFalse(state.isImportDialogVisible)

        // Export
        viewModel.processIntent(CertificateIntent.ExportCertificate(alias = "test-alias", destinationPath = "/destination/path"))
        advanceUntilIdle()
        state = viewModel.uiState.value
        assertFalse(state.isExportDialogVisible)

        // Select
        viewModel.processIntent(CertificateIntent.SelectCertificate(cert = state.clientCertificates[0]))
        advanceUntilIdle()
        state = viewModel.uiState.value
        assertNotNull(state.selectedCertificate)
        assertEquals("test-alias", state.selectedCertificate.alias)

        // Delete
        viewModel.processIntent(CertificateIntent.DeleteCertificate(alias = "test-alias"))
        advanceUntilIdle()
        state = viewModel.uiState.value
        assertTrue(state.clientCertificates.isEmpty())
        assertNull(state.selectedCertificate)
    }

    /**
     * Verifies rule management CRUD flows for mTLS matching wildcards.
     *
     * Design Intent: User rules configuration editor handles addition, modification, and removal.
     */
    @Test
    fun testMtlsRuleManagementIntents() = runTest {
        val rule = MtlsRuleSpec(ruleName = "my-rule", hostPattern = "*.knet.dev", certificateAlias = "knet-cert")

        // Add
        viewModel.processIntent(CertificateIntent.AddRule(rule = rule))
        advanceUntilIdle()
        var state = viewModel.uiState.value
        assertEquals(1, state.mtlsRules.size)
        assertEquals("my-rule", state.mtlsRules[0].ruleName)
        assertFalse(state.isRuleDialogVisible)

        // Edit
        val updatedRule = rule.copy(hostPattern = "*.knet.io")
        viewModel.processIntent(CertificateIntent.EditRule(rule = updatedRule))
        advanceUntilIdle()
        state = viewModel.uiState.value
        assertEquals(1, state.mtlsRules.size)
        assertEquals("*.knet.io", state.mtlsRules[0].hostPattern)

        // Remove
        viewModel.processIntent(CertificateIntent.RemoveRule(ruleName = "my-rule"))
        advanceUntilIdle()
        state = viewModel.uiState.value
        assertTrue(state.mtlsRules.isEmpty())
    }

    /**
     * Verifies that dialog visibility states toggle correctly.
     *
     * Design Intent: Modal window triggers bind visible states to close/open transitions.
     */
    @Test
    fun testDialogVisibilityIntents() = runTest {
        viewModel.processIntent(CertificateIntent.SetImportDialogVisible(true))
        assertTrue(viewModel.uiState.value.isImportDialogVisible)

        viewModel.processIntent(CertificateIntent.SetExportDialogVisible(true))
        assertTrue(viewModel.uiState.value.isExportDialogVisible)

        viewModel.processIntent(CertificateIntent.SetRuleDialogVisible(true))
        assertTrue(viewModel.uiState.value.isRuleDialogVisible)
    }
}
