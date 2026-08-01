package com.devuloopers.knet.ui.desktop.certificate.model

/**
 * Top-level state DTO for `:ui:desktop:certificate`.
 */
public data class CertificateState(
    val caStatus: CaStatus = CaStatus.MISSING,
    val caDetails: CaDetails = CaDetails(),
    val trustState: TrustInstallationState = TrustInstallationState.IDLE,
    val clientCertificates: List<ClientCertificate> = emptyList(),
    val mtlsRules: List<MtlsRule> = emptyList(),
    val selectedCertificate: ClientCertificate? = null,
    val isImportDialogVisible: Boolean = false,
    val isExportDialogVisible: Boolean = false,
    val isRuleDialogVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
 public val DialogOffset: Int = 100
