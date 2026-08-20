package com.devuloopers.knet.ui.desktop.certificate.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.core.components.dialog.AlertDialog
import com.devuloopers.knet.ui.core.components.dialog.ConfirmDialog
import com.devuloopers.knet.ui.core.components.drawer.KNetSideDrawer
import com.devuloopers.knet.ui.core.components.drawer.KNetSideDrawerSize
import com.devuloopers.knet.ui.desktop.certificate.client.CertificateCenterPanel
import com.devuloopers.knet.ui.desktop.certificate.client.ImportClientCertificateDialog
import com.devuloopers.knet.ui.desktop.certificate.model.CertificateIntent
import com.devuloopers.knet.ui.desktop.certificate.model.CertificateOperation
import com.devuloopers.knet.ui.desktop.certificate.model.TrustInstallationState
import com.devuloopers.knet.ui.desktop.certificate.mtls.AddEditMtlsRuleDialog
import com.devuloopers.knet.ui.desktop.certificate.overview.CertificateSidebar
import com.devuloopers.knet.ui.desktop.certificate.viewer.CertificateViewer
import com.devuloopers.knet.ui.desktop.certificate.viewmodel.CertificateViewModel

/**
 * CertificateManagerScreen is the main PKI and Root trust management dashboard.
 */
@Composable
fun CertificateManagerScreen(
    viewModel: CertificateViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val themeColors = KNetTheme.colors
    val onTrustAction = {
        viewModel.processIntent(
            if (
                uiState.trustState == TrustInstallationState.MANUAL_ACTION_REQUIRED &&
                uiState.manualTrustInstructions != null
            ) {
                CertificateIntent.ViewTrustInstructions
            } else {
                CertificateIntent.InstallTrust
            }
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val hasSelectedCert = uiState.selectedCertificate != null
        val minWidthForSidebar = if (hasSelectedCert) 1000.dp else 750.dp
        val showSidebar = maxWidth >= minWidthForSidebar
        val showInspectorInline = maxWidth >= 1180.dp

        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(themeColors.background)
        ) {
            // Left Column: Root CA and system trust status (hides cleanly on compact viewports to avoid crushing)
            if (showSidebar) {
                CertificateSidebar(
                    caDetails = uiState.caDetails,
                    caStatus = uiState.caStatus,
                    trustState = uiState.trustState,
                    platform = uiState.platform,
                    onInstallTrustClick = onTrustAction,
                )
            }

            // Center Column: Client certificates list and domain rule builders
            CertificateCenterPanel(
                uiState = uiState,
                onIntent = { viewModel.processIntent(it) },
                showTrustAction = !showSidebar,
                modifier = Modifier.weight(1f)
            )

            // Right Column: X.509 Inspector (Conditionally rendered ONLY when a certificate is selected)
            if (uiState.selectedCertificate != null && showInspectorInline) {
                CertificateViewer(
                    certificate = uiState.selectedCertificate,
                    onClose = { viewModel.processIntent(CertificateIntent.SelectCertificate(null)) }
                )
            }
        }

        if (!showSidebar) {
            KNetSideDrawer(
                visible = uiState.isTrustDrawerVisible,
                size = KNetSideDrawerSize.STANDARD,
            ) {
                CertificateSidebar(
                    caDetails = uiState.caDetails,
                    caStatus = uiState.caStatus,
                    trustState = uiState.trustState,
                    platform = uiState.platform,
                    onInstallTrustClick = onTrustAction,
                    onClose = { viewModel.processIntent(CertificateIntent.SetTrustDrawerVisible(false)) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (uiState.selectedCertificate != null && !showInspectorInline) {
            KNetSideDrawer(visible = true, size = KNetSideDrawerSize.STANDARD) {
                CertificateViewer(
                    certificate = uiState.selectedCertificate,
                    onClose = { viewModel.processIntent(CertificateIntent.SelectCertificate(null)) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Dialog Overlays
        if (uiState.isImportDialogVisible) {
            ImportClientCertificateDialog(
                onDismiss = { viewModel.processIntent(CertificateIntent.SetImportDialogVisible(false)) },
                onImport = { alias, path, passphrase ->
                    viewModel.processIntent(CertificateIntent.ImportCertificate(path = path, alias = alias, passphrase = passphrase))
                },
                errorMessage = uiState.errorMessage,
                isImporting = uiState.activeOperation == CertificateOperation.IMPORT_CERTIFICATE,
            )
        }

        if (uiState.isRuleDialogVisible) {
            AddEditMtlsRuleDialog(
                availableCertificates = uiState.clientCertificates,
                onDismiss = { viewModel.processIntent(CertificateIntent.SetRuleDialogVisible(false)) },
                onSave = { rule ->
                    viewModel.processIntent(
                        if (uiState.editingRule == null) CertificateIntent.AddRule(rule)
                        else CertificateIntent.EditRule(rule)
                    )
                },
                initialRule = uiState.editingRule,
                errorMessage = uiState.errorMessage,
                isSaving = uiState.activeOperation == CertificateOperation.SAVE_RULE,
            )
        }

        uiState.pendingCertificateDeletionAlias?.let { alias ->
            val dependentRules = uiState.mtlsRules.count { it.certificateAlias == alias }
            ConfirmDialog(
                title = "Delete client certificate?",
                message = buildString {
                    append("This permanently removes '$alias' and its stored private-key material.")
                    if (dependentRules > 0) append(" $dependentRules dependent mTLS rule(s) will also be removed.")
                },
                confirmText = "Delete",
                onConfirm = { viewModel.processIntent(CertificateIntent.ConfirmDeleteCertificate) },
                onDismissRequest = { viewModel.processIntent(CertificateIntent.DismissDeleteCertificate) },
            )
        }

        uiState.pendingRuleDeletionName?.let { ruleName ->
            ConfirmDialog(
                title = "Delete mTLS rule?",
                message = "The rule '$ruleName' will no longer select a client identity for matching hosts.",
                confirmText = "Delete",
                onConfirm = { viewModel.processIntent(CertificateIntent.ConfirmRemoveRule) },
                onDismissRequest = { viewModel.processIntent(CertificateIntent.DismissRemoveRule) },
            )
        }

        if (uiState.isTrustInstructionsVisible) {
            AlertDialog(
                title = "Install KNet Root CA",
                message = uiState.manualTrustInstructions.orEmpty(),
                onDismissRequest = {
                    viewModel.processIntent(CertificateIntent.DismissTrustInstructions)
                },
            )
        }
    }
}
