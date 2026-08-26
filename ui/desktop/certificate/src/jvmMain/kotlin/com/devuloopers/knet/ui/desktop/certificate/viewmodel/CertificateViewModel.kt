package com.devuloopers.knet.ui.desktop.certificate.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.application.contract.certificate.CertificateManagement
import com.devuloopers.knet.application.contract.certificate.MtlsRuleSpec
import com.devuloopers.knet.application.contract.certificate.TrustInstallationResult
import com.devuloopers.knet.ui.desktop.certificate.model.CertificateIntent
import com.devuloopers.knet.ui.desktop.certificate.model.CertificateOperation
import com.devuloopers.knet.ui.desktop.certificate.model.CertificateState
import com.devuloopers.knet.ui.desktop.certificate.model.TrustInstallationState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Owns the certificate workspace state and serializes all persistent mutations. */
class CertificateViewModel(
    private val certificateManager: CertificateManagement,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CertificateState(isLoading = true))
    private val mutationMutex = Mutex()
    private var refreshJob: Job? = null

    val uiState: StateFlow<CertificateState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun processIntent(intent: CertificateIntent) {
        when (intent) {
            CertificateIntent.Refresh -> refresh()
            CertificateIntent.InstallTrust -> installTrust()
            is CertificateIntent.ImportCertificate -> importCertificate(intent)
            is CertificateIntent.ExportCertificate -> exportCertificate(intent)
            is CertificateIntent.RequestDeleteCertificate -> _uiState.update {
                it.copy(pendingCertificateDeletionAlias = intent.alias)
            }
            CertificateIntent.ConfirmDeleteCertificate -> deleteCertificate()
            CertificateIntent.DismissDeleteCertificate -> _uiState.update {
                it.copy(pendingCertificateDeletionAlias = null)
            }
            is CertificateIntent.AddRule -> saveRule(intent.rule, editing = false)
            is CertificateIntent.EditRule -> saveRule(intent.rule, editing = true)
            is CertificateIntent.RequestRemoveRule -> _uiState.update {
                it.copy(pendingRuleDeletionName = intent.ruleName)
            }
            CertificateIntent.ConfirmRemoveRule -> deleteRule()
            CertificateIntent.DismissRemoveRule -> _uiState.update { it.copy(pendingRuleDeletionName = null) }
            is CertificateIntent.SelectCertificate -> _uiState.update {
                it.copy(
                    selectedCertificateAlias = intent.alias,
                    isTrustDrawerVisible = if (intent.alias != null) false else it.isTrustDrawerVisible,
                )
            }
            is CertificateIntent.SetImportDialogVisible -> _uiState.update {
                it.copy(isImportDialogVisible = intent.visible, errorMessage = null)
            }
            is CertificateIntent.SetExportDialogVisible -> _uiState.update {
                it.copy(isExportDialogVisible = intent.visible, errorMessage = null)
            }
            is CertificateIntent.SetRuleDialogVisible -> _uiState.update {
                it.copy(
                    isRuleDialogVisible = intent.visible,
                    editingRule = intent.rule.takeIf { _ -> intent.visible },
                    errorMessage = null,
                )
            }
            is CertificateIntent.SetTrustDrawerVisible -> _uiState.update {
                it.copy(
                    isTrustDrawerVisible = intent.visible,
                    selectedCertificateAlias = if (intent.visible) null else it.selectedCertificateAlias,
                )
            }
            CertificateIntent.ViewTrustInstructions -> _uiState.update {
                it.copy(isTrustInstructionsVisible = it.manualTrustInstructions != null)
            }
            CertificateIntent.DismissTrustInstructions -> _uiState.update {
                it.copy(isTrustInstructionsVisible = false)
            }
            is CertificateIntent.SwitchTab -> _uiState.update { it.copy(activeTab = intent.tab) }
            is CertificateIntent.SwitchSidebarItem -> _uiState.update { it.copy(activeSidebarItem = intent.item) }
            is CertificateIntent.ToggleCertificateEnabled -> toggleCertificate(intent.alias, intent.enabled)
            is CertificateIntent.ToggleRuleEnabled -> saveRule(
                rule = intent.rule.copy(enabled = intent.enabled),
                editing = true,
                operation = CertificateOperation.TOGGLE_RULE,
            )
            is CertificateIntent.Search -> _uiState.update { it.copy(searchQuery = intent.query) }
            CertificateIntent.ClearMessage -> _uiState.update {
                it.copy(errorMessage = null, informationMessage = null)
            }
        }
    }

    private fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            mutationMutex.withLock {
                _uiState.update {
                    it.copy(
                        isLoading = true,
                        activeOperation = CertificateOperation.REFRESH,
                        errorMessage = null,
                    )
                }
                try {
                    val snapshot = withContext(ioDispatcher) {
                        coroutineScope {
                            val authority = async { certificateManager.authoritySummary() }
                            val certificates = async { certificateManager.clientCertificates() }
                            val rules = async { certificateManager.mtlsRules() }
                            Triple(authority.await(), certificates.await(), rules.await())
                        }
                    }
                    _uiState.update { current ->
                        val (authority, certificates, rules) = snapshot
                        current.copy(
                            caStatus = authority.status,
                            caDetails = authority,
                            clientCertificates = certificates,
                            mtlsRules = rules,
                            selectedCertificateAlias = current.selectedCertificateAlias
                                ?.takeIf { alias -> certificates.any { it.alias == alias } },
                            trustState = when {
                                authority.trustedByOperatingSystem -> TrustInstallationState.INSTALLED
                                current.manualTrustInstructions != null -> TrustInstallationState.MANUAL_ACTION_REQUIRED
                                else -> TrustInstallationState.IDLE
                            },
                            manualTrustInstructions = current.manualTrustInstructions
                                .takeUnless { authority.trustedByOperatingSystem },
                            isTrustInstructionsVisible = current.isTrustInstructionsVisible &&
                                !authority.trustedByOperatingSystem,
                            isLoading = false,
                            activeOperation = null,
                        )
                    }
                } catch (error: Exception) {
                    finishWithError(error)
                }
            }
        }
    }

    private fun installTrust() = runOperation(CertificateOperation.INSTALL_TRUST) {
        _uiState.update { it.copy(trustState = TrustInstallationState.INSTALLING) }
        when (val result = withContext(ioDispatcher) { certificateManager.installRootCertificate() }) {
            TrustInstallationResult.Installed -> _uiState.update {
                it.copy(
                    trustState = TrustInstallationState.INSTALLED,
                    informationMessage = "KNet Root CA is trusted by this operating system.",
                    manualTrustInstructions = null,
                    isTrustInstructionsVisible = false,
                )
            }
            is TrustInstallationResult.ManualActionRequired -> _uiState.update {
                it.copy(
                    trustState = TrustInstallationState.MANUAL_ACTION_REQUIRED,
                    informationMessage = result.message,
                    manualTrustInstructions = result.instructions,
                    isTrustInstructionsVisible = true,
                )
            }
            is TrustInstallationResult.Failed -> _uiState.update {
                it.copy(
                    trustState = TrustInstallationState.FAILED,
                    errorMessage = result.message,
                    manualTrustInstructions = null,
                    isTrustInstructionsVisible = false,
                )
            }
        }
    }

    private fun importCertificate(intent: CertificateIntent.ImportCertificate) =
        runOperation(CertificateOperation.IMPORT_CERTIFICATE) {
            val certificates = withContext(ioDispatcher) {
                certificateManager.importClientCertificate(intent.path, intent.alias, intent.passphrase)
                certificateManager.clientCertificates()
            }
            _uiState.update {
                it.copy(
                    clientCertificates = certificates,
                    selectedCertificateAlias = intent.alias.trim(),
                    isImportDialogVisible = false,
                    informationMessage = "Client certificate '${intent.alias.trim()}' imported.",
                )
            }
        }

    private fun exportCertificate(intent: CertificateIntent.ExportCertificate) =
        runOperation(CertificateOperation.EXPORT_CERTIFICATE) {
            withContext(ioDispatcher) {
                certificateManager.exportClientCertificate(intent.alias, intent.destinationPath)
            }
            _uiState.update {
                it.copy(
                    isExportDialogVisible = false,
                    informationMessage = "Client certificate '${intent.alias}' exported.",
                )
            }
        }

    private fun deleteCertificate() {
        val alias = _uiState.value.pendingCertificateDeletionAlias ?: return
        runOperation(CertificateOperation.DELETE_CERTIFICATE) {
            val (certificates, rules) = withContext(ioDispatcher) {
                certificateManager.deleteClientCertificate(alias)
                certificateManager.clientCertificates() to certificateManager.mtlsRules()
            }
            _uiState.update {
                it.copy(
                    clientCertificates = certificates,
                    mtlsRules = rules,
                    selectedCertificateAlias = it.selectedCertificateAlias.takeUnless { selected -> selected == alias },
                    pendingCertificateDeletionAlias = null,
                    informationMessage = "Client certificate '$alias' and its mTLS mappings were deleted.",
                )
            }
        }
    }

    private fun saveRule(
        rule: MtlsRuleSpec,
        editing: Boolean,
        operation: CertificateOperation = CertificateOperation.SAVE_RULE,
    ) = runOperation(operation) {
        val rules = withContext(ioDispatcher) {
            if (editing) certificateManager.editMtlsRule(rule) else certificateManager.addMtlsRule(rule)
            certificateManager.mtlsRules()
        }
        _uiState.update {
            it.copy(
                mtlsRules = rules,
                isRuleDialogVisible = false,
                editingRule = null,
                informationMessage = "mTLS rule '${rule.ruleName}' saved.",
            )
        }
    }

    private fun deleteRule() {
        val ruleName = _uiState.value.pendingRuleDeletionName ?: return
        runOperation(CertificateOperation.DELETE_RULE) {
            val rules = withContext(ioDispatcher) {
                certificateManager.deleteMtlsRule(ruleName)
                certificateManager.mtlsRules()
            }
            _uiState.update {
                it.copy(
                    mtlsRules = rules,
                    pendingRuleDeletionName = null,
                    informationMessage = "mTLS rule '$ruleName' deleted.",
                )
            }
        }
    }

    private fun toggleCertificate(alias: String, enabled: Boolean) =
        runOperation(CertificateOperation.TOGGLE_CERTIFICATE) {
            val certificates = withContext(ioDispatcher) {
                certificateManager.setClientCertificateEnabled(alias, enabled)
                certificateManager.clientCertificates()
            }
            _uiState.update { it.copy(clientCertificates = certificates) }
        }

    private fun runOperation(operation: CertificateOperation, block: suspend () -> Unit): Job =
        viewModelScope.launch {
            mutationMutex.withLock {
                _uiState.update {
                    it.copy(activeOperation = operation, errorMessage = null, informationMessage = null)
                }
                try {
                    block()
                } catch (error: Exception) {
                    finishWithError(error)
                } finally {
                    _uiState.update {
                        it.copy(
                            activeOperation = null,
                            isLoading = false,
                            trustState = if (
                                operation == CertificateOperation.INSTALL_TRUST &&
                                it.trustState == TrustInstallationState.INSTALLING
                            ) TrustInstallationState.FAILED else it.trustState,
                        )
                    }
                }
            }
        }

    private fun finishWithError(error: Exception) {
        _uiState.update {
            it.copy(
                isLoading = false,
                activeOperation = null,
                errorMessage = error.message ?: "Certificate operation failed.",
            )
        }
    }
}
