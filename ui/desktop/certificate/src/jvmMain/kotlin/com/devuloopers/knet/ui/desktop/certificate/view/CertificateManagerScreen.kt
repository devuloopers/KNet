package com.devuloopers.knet.ui.desktop.certificate.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.certificate.client.CertificateCenterPanel
import com.devuloopers.knet.ui.desktop.certificate.client.ImportClientCertificateDialog
import com.devuloopers.knet.ui.desktop.certificate.model.CertificateIntent
import com.devuloopers.knet.ui.desktop.certificate.mtls.AddEditMtlsRuleDialog
import com.devuloopers.knet.ui.desktop.certificate.overview.CertificateSidebar
import com.devuloopers.knet.ui.desktop.certificate.viewer.CertificateViewer
import com.devuloopers.knet.ui.desktop.certificate.viewmodel.CertificateViewModel

/**
 * CertificateManagerScreen is the main PKI and Root trust management dashboard.
 */
@Composable
public fun CertificateManagerScreen(
    viewModel: CertificateViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val themeColors = KNetTheme.colors

    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(themeColors.background)
        ) {
            // Left Column: Root CA and system trust status
            CertificateSidebar(
                caDetails = uiState.caDetails,
                trustState = uiState.trustState,
                platform = uiState.platform,
                onInstallTrustClick = { viewModel.processIntent(CertificateIntent.InstallTrust) }
            )


            // Center Column: Client certificates list and domain rule builders
            CertificateCenterPanel(
                uiState = uiState,
                onIntent = { viewModel.processIntent(it) },
                modifier = Modifier.weight(1f)
            )

            // Right Column: X.509 Inspector (Conditionally rendered ONLY when a certificate is selected)
            if (uiState.selectedCertificate != null) {
                CertificateViewer(
                    certificate = uiState.selectedCertificate,
                    onClose = { viewModel.processIntent(CertificateIntent.SelectCertificate(null)) }
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
                errorMessage = uiState.errorMessage
            )
        }

        if (uiState.isRuleDialogVisible) {
            AddEditMtlsRuleDialog(
                availableCertificates = uiState.clientCertificates,
                onDismiss = { viewModel.processIntent(CertificateIntent.SetRuleDialogVisible(false)) },
                onSave = { rule ->
                    viewModel.processIntent(CertificateIntent.AddRule(rule))
                }
            )
        }
    }
}
