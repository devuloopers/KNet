package com.devuloopers.knet.ui.desktop.certificate.model

import com.devuloopers.knet.application.port.certificate.CertificateAuthoritySummary
import com.devuloopers.knet.application.port.certificate.ClientCertificateSummary
import com.devuloopers.knet.application.port.certificate.MtlsRuleSpec

/**
 * Top-level state DTO for `:ui:desktop:certificate`.
 */
data class CertificateState(
    val caStatus: CaStatus = CaStatus.MISSING,
    val caDetails: CertificateAuthoritySummary = CertificateAuthoritySummary(),
    val trustState: TrustInstallationState = TrustInstallationState.CHECKING,
    val platform: HostPlatform = HostPlatform.current(),
    val clientCertificates: List<ClientCertificateSummary> = emptyList(),
    val mtlsRules: List<MtlsRuleSpec> = emptyList(),
    val selectedCertificate: ClientCertificateSummary? = null,
    val isImportDialogVisible: Boolean = false,
    val isExportDialogVisible: Boolean = false,
    val isRuleDialogVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val activeTab: CertificateTab = CertificateTab.CLIENT_CERTS,
    val activeSidebarItem: CertificateSidebarItem = CertificateSidebarItem.ROOT_CAS,
    val searchQuery: String = ""
)


val DialogOffset: Int = 100
