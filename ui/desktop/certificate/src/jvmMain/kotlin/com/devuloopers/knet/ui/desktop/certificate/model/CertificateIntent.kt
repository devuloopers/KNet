package com.devuloopers.knet.ui.desktop.certificate.model

/**
 * Sealed interface representing UI user actions in KNet Certificate manager.
 */
public sealed interface CertificateIntent {
    public object Refresh : CertificateIntent
    public object InstallTrust : CertificateIntent
    public data class ImportCertificate(val path: String, val alias: String) : CertificateIntent
    public data class ExportCertificate(val alias: String, val destinationPath: String) : CertificateIntent
    public data class DeleteCertificate(val alias: String) : CertificateIntent
    public data class AddRule(val rule: MtlsRule) : CertificateIntent
    public data class EditRule(val rule: MtlsRule) : CertificateIntent
    public data class RemoveRule(val ruleName: String) : CertificateIntent
    public data class SelectCertificate(val cert: ClientCertificate?) : CertificateIntent
    public data class SetImportDialogVisible(val visible: Boolean) : CertificateIntent
    public data class SetExportDialogVisible(val visible: Boolean) : CertificateIntent
    public data class SetRuleDialogVisible(val visible: Boolean) : CertificateIntent
}
