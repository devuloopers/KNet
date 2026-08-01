package com.devuloopers.knet.ui.desktop.certificate.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.desktop.certificate.model.CertificateIntent
import com.devuloopers.knet.ui.desktop.certificate.viewmodel.CertificateViewModel
import com.devuloopers.knet.ui.desktop.certificate.overview.CertificateOverview
import com.devuloopers.knet.ui.desktop.certificate.trust.TrustWizard
import com.devuloopers.knet.ui.desktop.certificate.trust.TrustStatusCard
import com.devuloopers.knet.ui.desktop.certificate.trust.InstallationGuide
import com.devuloopers.knet.ui.desktop.certificate.client.ClientCertificateList
import com.devuloopers.knet.ui.desktop.certificate.client.ClientCertificateDialog
import com.devuloopers.knet.ui.desktop.certificate.client.ImportCertificateDialog
import com.devuloopers.knet.ui.desktop.certificate.client.ExportCertificateDialog
import com.devuloopers.knet.ui.desktop.certificate.mtls.MtlsRuleList
import com.devuloopers.knet.ui.desktop.certificate.mtls.MtlsRuleDialog
import com.devuloopers.knet.ui.desktop.certificate.mtls.HostCertificateMapping
import com.devuloopers.knet.ui.desktop.certificate.viewer.CertificateViewer

/**
 * CertificateManagerScreen is the main PKI and Root trust management dashboard.
 */
@Composable
public fun CertificateManagerScreen(
    viewModel: CertificateViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(KNetColors.BackgroundDark)
    ) {
        // Left Column: Root CA and trust store settings
        Column(
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight()
                .padding(8.dp)
        ) {
            CertificateOverview(status = uiState.caStatus, details = uiState.caDetails)
            TrustStatusCard(state = uiState.trustState)
            TrustWizard(
                state = uiState.trustState,
                onInstallTrust = { viewModel.processIntent(CertificateIntent.InstallTrust) }
            )
            InstallationGuide()
        }

        // Center Column: Client certificates list and rule builders
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(8.dp)
        ) {
            ClientCertificateList(
                certificates = uiState.clientCertificates,
                selectedCertificate = uiState.selectedCertificate,
                onSelect = { viewModel.processIntent(CertificateIntent.SelectCertificate(it)) },
                onDelete = { viewModel.processIntent(CertificateIntent.DeleteCertificate(it.alias)) }
            )
            MtlsRuleList(
                rules = uiState.mtlsRules,
                onRemoveRule = { viewModel.processIntent(CertificateIntent.RemoveRule(it)) }
            )
            HostCertificateMapping(rules = uiState.mtlsRules)
        }

        // Right Column: Certificate detailed X.509 values inspector
        Column(
            modifier = Modifier
                .width(320.dp)
                .fillMaxHeight()
                .padding(8.dp)
        ) {
            CertificateViewer(details = uiState.caDetails)
        }
    }
}
