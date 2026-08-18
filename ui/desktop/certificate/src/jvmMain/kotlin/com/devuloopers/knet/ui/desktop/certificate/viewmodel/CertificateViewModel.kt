package com.devuloopers.knet.ui.desktop.certificate.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.application.port.certificate.CertificateManagementPort
import com.devuloopers.knet.ui.desktop.certificate.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel managing Certificate manager status, trust wizards, client identities, and mTLS rules.
 *
 * It delegates PKI and mutual TLS configuration through the application boundary.
 *
 * @property certificateManager The engine facade instance injected via constructor.
 * @property ioDispatcher Coroutine dispatcher for asynchronous I/O operations (defaults to [Dispatchers.IO]).
 */
class CertificateViewModel(
    private val certificateManager: CertificateManagementPort,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState: MutableStateFlow<CertificateState> = MutableStateFlow(CertificateState(isLoading = true))

    /**
     * Exposes the read-only unidirectional state flow for UI mapping.
     */
    val uiState: StateFlow<CertificateState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    /**
     * Loads or reloads CA details, client certificates, and mTLS rules into the view state from the engine.
     */
    private fun loadData() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val authority = withContext(ioDispatcher) { certificateManager.authoritySummary() }
                val statusStr = authority.status
                val caStatus = try {
                    CaStatus.valueOf(statusStr)
                } catch (_: Exception) {
                    CaStatus.INVALID
                }

                val certs = withContext(ioDispatcher) {
                    certificateManager.clientCertificates()
                }

                val rules = withContext(ioDispatcher) {
                    certificateManager.mtlsRules()
                }

                val detectedTrustState = withContext(ioDispatcher) {
                    if (authority.trustedByOperatingSystem) {
                        TrustInstallationState.INSTALLED
                    } else {
                        TrustInstallationState.IDLE
                    }
                }

                _uiState.update {
                    it.copy(
                        caStatus = caStatus,
                        caDetails = authority,
                        clientCertificates = certs,
                        mtlsRules = rules,
                        trustState = detectedTrustState,
                        isLoading = false,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message
                    )
                }
            }
        }
    }

    /**
     * Processes incoming user actions (intents) asynchronously by delegating to the certificate engine.
     * All disk operations and heavy parsing tasks are explicitly dispatched to [ioDispatcher].
     *
     * @param intent The [CertificateIntent] UI user action model to execute.
     */
    fun processIntent(intent: CertificateIntent) {
        viewModelScope.launch {
            when (intent) {
                CertificateIntent.Refresh -> loadData()
                CertificateIntent.InstallTrust -> {
                    _uiState.update { it.copy(trustState = TrustInstallationState.INSTALLING) }
                    try {
                        val success = withContext(ioDispatcher) { certificateManager.installRootCertificate() }
                        if (success) {
                            _uiState.update { it.copy(trustState = TrustInstallationState.INSTALLED) }
                        } else {
                            _uiState.update {
                                it.copy(
                                    trustState = TrustInstallationState.FAILED,
                                    errorMessage = "Operating system trust store registration failed."
                                )
                            }
                        }
                    } catch (e: Exception) {
                        _uiState.update {
                            it.copy(
                                trustState = TrustInstallationState.FAILED,
                                errorMessage = e.message
                            )
                        }
                    }
                }

                is CertificateIntent.ImportCertificate -> {
                    try {
                        val certs = withContext(ioDispatcher) {
                            certificateManager.importClientCertificate(intent.path, intent.alias, intent.passphrase)
                            certificateManager.clientCertificates()
                        }
                        _uiState.update {
                            it.copy(
                                clientCertificates = certs,
                                isImportDialogVisible = false,
                                errorMessage = null
                            )
                        }
                    } catch (e: Exception) {
                        _uiState.update { it.copy(errorMessage = e.message) }
                    }
                }

                is CertificateIntent.ExportCertificate -> {
                    try {
                        withContext(ioDispatcher) {
                            certificateManager.exportClientCertificate(
                                intent.alias,
                                intent.destinationPath
                            )
                        }
                        _uiState.update {
                            it.copy(
                                isExportDialogVisible = false,
                                errorMessage = null
                            )
                        }
                    } catch (e: Exception) {
                        _uiState.update { it.copy(errorMessage = e.message) }
                    }
                }

                is CertificateIntent.DeleteCertificate -> {
                    try {
                        val certs = withContext(ioDispatcher) {
                            certificateManager.deleteClientCertificate(intent.alias)
                            certificateManager.clientCertificates()
                        }
                        _uiState.update {
                            it.copy(
                                clientCertificates = certs,
                                selectedCertificate = if (it.selectedCertificate?.alias == intent.alias) null else it.selectedCertificate,
                                errorMessage = null
                            )
                        }
                    } catch (e: Exception) {
                        _uiState.update { it.copy(errorMessage = e.message) }
                    }
                }

                is CertificateIntent.AddRule -> {
                    try {
                        val rules = withContext(ioDispatcher) {
                            certificateManager.addMtlsRule(intent.rule)
                            certificateManager.mtlsRules()
                        }
                        _uiState.update {
                            it.copy(
                                mtlsRules = rules,
                                isRuleDialogVisible = false,
                                errorMessage = null
                            )
                        }
                    } catch (e: Exception) {
                        _uiState.update { it.copy(errorMessage = e.message) }
                    }
                }

                is CertificateIntent.EditRule -> {
                    try {
                        val rules = withContext(ioDispatcher) {
                            certificateManager.editMtlsRule(intent.rule)
                            certificateManager.mtlsRules()
                        }
                        _uiState.update {
                            it.copy(
                                mtlsRules = rules,
                                isRuleDialogVisible = false,
                                errorMessage = null
                            )
                        }
                    } catch (e: Exception) {
                        _uiState.update { it.copy(errorMessage = e.message) }
                    }
                }

                is CertificateIntent.RemoveRule -> {
                    try {
                        val rules = withContext(ioDispatcher) {
                            certificateManager.deleteMtlsRule(intent.ruleName)
                            certificateManager.mtlsRules()
                        }
                        _uiState.update {
                            it.copy(
                                mtlsRules = rules,
                                errorMessage = null
                            )
                        }
                    } catch (e: Exception) {
                        _uiState.update { it.copy(errorMessage = e.message) }
                    }
                }

                is CertificateIntent.SelectCertificate -> {
                    _uiState.update { it.copy(selectedCertificate = intent.cert) }
                }

                is CertificateIntent.SetImportDialogVisible -> {
                    _uiState.update { it.copy(isImportDialogVisible = intent.visible) }
                }

                is CertificateIntent.SetExportDialogVisible -> {
                    _uiState.update { it.copy(isExportDialogVisible = intent.visible) }
                }

                is CertificateIntent.SetRuleDialogVisible -> {
                    _uiState.update { it.copy(isRuleDialogVisible = intent.visible) }
                }

                is CertificateIntent.SwitchTab -> {
                    _uiState.update { it.copy(activeTab = intent.tab) }
                }

                is CertificateIntent.SwitchSidebarItem -> {
                    _uiState.update { it.copy(activeSidebarItem = intent.item) }
                }

                is CertificateIntent.ToggleCertificateEnabled -> {
                    try {
                        val certs = withContext(ioDispatcher) {
                            certificateManager.setClientCertificateEnabled(intent.alias, intent.enabled)
                            certificateManager.clientCertificates()
                        }
                        _uiState.update { it.copy(clientCertificates = certs) }
                    } catch (e: Exception) {
                        _uiState.update { it.copy(errorMessage = e.message) }
                    }
                }

                is CertificateIntent.Search -> {
                    _uiState.update { it.copy(searchQuery = intent.query) }
                }
            }
        }
    }
}
