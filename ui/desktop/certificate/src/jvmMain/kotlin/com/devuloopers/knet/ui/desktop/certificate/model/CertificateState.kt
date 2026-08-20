package com.devuloopers.knet.ui.desktop.certificate.model

import com.devuloopers.knet.application.port.certificate.CertificateAuthoritySummary
import com.devuloopers.knet.application.port.certificate.CertificateAuthorityStatus
import com.devuloopers.knet.application.port.certificate.ClientCertificateSummary
import com.devuloopers.knet.application.port.certificate.MtlsRuleSpec

/**
 * Top-level state DTO for `:ui:desktop:certificate`.
 */
data class CertificateState(
    val caStatus: CertificateAuthorityStatus = CertificateAuthorityStatus.MISSING,
    val caDetails: CertificateAuthoritySummary = CertificateAuthoritySummary(),
    val trustState: TrustInstallationState = TrustInstallationState.CHECKING,
    val platform: HostPlatform = HostPlatform.current(),
    val clientCertificates: List<ClientCertificateSummary> = emptyList(),
    val mtlsRules: List<MtlsRuleSpec> = emptyList(),
    val selectedCertificateAlias: String? = null,
    val isImportDialogVisible: Boolean = false,
    val isExportDialogVisible: Boolean = false,
    val isRuleDialogVisible: Boolean = false,
    val editingRule: MtlsRuleSpec? = null,
    val pendingCertificateDeletionAlias: String? = null,
    val pendingRuleDeletionName: String? = null,
    val isTrustDrawerVisible: Boolean = false,
    val manualTrustInstructions: String? = null,
    val isTrustInstructionsVisible: Boolean = false,
    val isLoading: Boolean = false,
    val activeOperation: CertificateOperation? = null,
    val errorMessage: String? = null,
    val informationMessage: String? = null,
    val activeTab: CertificateTab = CertificateTab.CLIENT_CERTS,
    val activeSidebarItem: CertificateSidebarItem = CertificateSidebarItem.ROOT_CAS,
    val searchQuery: String = ""
) {
    val selectedCertificate: ClientCertificateSummary?
        get() = clientCertificates.firstOrNull { it.alias == selectedCertificateAlias }
}

enum class CertificateOperation {
    REFRESH,
    INSTALL_TRUST,
    IMPORT_CERTIFICATE,
    EXPORT_CERTIFICATE,
    DELETE_CERTIFICATE,
    SAVE_RULE,
    DELETE_RULE,
    TOGGLE_CERTIFICATE,
    TOGGLE_RULE,
}
