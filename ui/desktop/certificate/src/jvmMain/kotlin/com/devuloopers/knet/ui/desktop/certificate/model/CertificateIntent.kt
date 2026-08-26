package com.devuloopers.knet.ui.desktop.certificate.model

import com.devuloopers.knet.application.contract.certificate.MtlsRuleSpec

/**
 * Sealed interface representing UI user actions in KNet Certificate manager.
 */
sealed interface CertificateIntent {
    object Refresh : CertificateIntent
    object InstallTrust : CertificateIntent
    data class ImportCertificate(val path: String, val alias: String, val passphrase: String = "") : CertificateIntent
    data class ExportCertificate(val alias: String, val destinationPath: String) : CertificateIntent
    data class RequestDeleteCertificate(val alias: String) : CertificateIntent
    data object ConfirmDeleteCertificate : CertificateIntent
    data object DismissDeleteCertificate : CertificateIntent
    data class AddRule(val rule: MtlsRuleSpec) : CertificateIntent
    data class EditRule(val rule: MtlsRuleSpec) : CertificateIntent
    data class RequestRemoveRule(val ruleName: String) : CertificateIntent
    data object ConfirmRemoveRule : CertificateIntent
    data object DismissRemoveRule : CertificateIntent
    data class SelectCertificate(val alias: String?) : CertificateIntent
    data class SetImportDialogVisible(val visible: Boolean) : CertificateIntent
    data class SetExportDialogVisible(val visible: Boolean) : CertificateIntent
    data class SetRuleDialogVisible(val visible: Boolean, val rule: MtlsRuleSpec? = null) : CertificateIntent
    data class SetTrustDrawerVisible(val visible: Boolean) : CertificateIntent
    data object ViewTrustInstructions : CertificateIntent
    data object DismissTrustInstructions : CertificateIntent
    data class SwitchTab(val tab: CertificateTab) : CertificateIntent
    data class SwitchSidebarItem(val item: CertificateSidebarItem) : CertificateIntent
    data class ToggleCertificateEnabled(val alias: String, val enabled: Boolean) : CertificateIntent
    data class ToggleRuleEnabled(val rule: MtlsRuleSpec, val enabled: Boolean) : CertificateIntent
    data class Search(val query: String) : CertificateIntent
    data object ClearMessage : CertificateIntent
}
