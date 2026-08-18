package com.devuloopers.knet.ui.desktop.certificate.model

import com.devuloopers.knet.application.port.certificate.ClientCertificateSummary
import com.devuloopers.knet.application.port.certificate.MtlsRuleSpec

/**
 * Sealed interface representing UI user actions in KNet Certificate manager.
 */
sealed interface CertificateIntent {
    object Refresh : CertificateIntent
    object InstallTrust : CertificateIntent
    data class ImportCertificate(val path: String, val alias: String, val passphrase: String = "") : CertificateIntent
    data class ExportCertificate(val alias: String, val destinationPath: String) : CertificateIntent
    data class DeleteCertificate(val alias: String) : CertificateIntent
    data class AddRule(val rule: MtlsRuleSpec) : CertificateIntent
    data class EditRule(val rule: MtlsRuleSpec) : CertificateIntent
    data class RemoveRule(val ruleName: String) : CertificateIntent
    data class SelectCertificate(val cert: ClientCertificateSummary?) : CertificateIntent
    data class SetImportDialogVisible(val visible: Boolean) : CertificateIntent
    data class SetExportDialogVisible(val visible: Boolean) : CertificateIntent
    data class SetRuleDialogVisible(val visible: Boolean) : CertificateIntent
    data class SwitchTab(val tab: CertificateTab) : CertificateIntent
    data class SwitchSidebarItem(val item: CertificateSidebarItem) : CertificateIntent
    data class ToggleCertificateEnabled(val alias: String, val enabled: Boolean) : CertificateIntent
    data class Search(val query: String) : CertificateIntent
}
