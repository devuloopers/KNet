package com.devuloopers.knet.ui.desktop.certificate.client

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.certificate.model.CertificateIntent
import com.devuloopers.knet.ui.desktop.certificate.model.CertificateState
import com.devuloopers.knet.ui.desktop.certificate.model.CertificateTab
import com.devuloopers.knet.ui.desktop.certificate.mtls.MtlsRuleList

@Composable
fun CertificateCenterPanel(
    uiState: CertificateState,
    onIntent: (CertificateIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val query = uiState.searchQuery.trim().lowercase()

    val filteredCerts = if (query.isEmpty()) {
        uiState.clientCertificates
    } else {
        uiState.clientCertificates.filter {
            it.alias.lowercase().contains(query) || it.host.lowercase().contains(query) || it.subject.lowercase().contains(query)
        }
    }

    val filteredRules = if (query.isEmpty()) {
        uiState.mtlsRules
    } else {
        uiState.mtlsRules.filter {
            it.ruleName.lowercase().contains(query) || it.hostPattern.lowercase().contains(query) || it.certificateAlias.lowercase().contains(query)
        }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(themeColors.background)
    ) {
        // Tab Bar
        CertificateTabBar(
            activeTab = uiState.activeTab,
            onTabSelected = { onIntent(CertificateIntent.SwitchTab(it)) }
        )

        // Action Bar (Search + Import/Add buttons)
        CertificateActionBar(
            activeTab = uiState.activeTab,
            searchQuery = uiState.searchQuery,
            onSearchChange = { onIntent(CertificateIntent.Search(it)) },
            onImportClick = { onIntent(CertificateIntent.SetImportDialogVisible(true)) },
            onAddRuleClick = { onIntent(CertificateIntent.SetRuleDialogVisible(true)) }
        )

        // Content
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
            when (uiState.activeTab) {
                CertificateTab.CLIENT_CERTS -> {
                    ClientCertificateList(
                        certificates = filteredCerts,
                        selectedCertificate = uiState.selectedCertificate,
                        onSelect = { onIntent(CertificateIntent.SelectCertificate(it)) },
                        onToggleEnabled = { alias, enabled -> onIntent(CertificateIntent.ToggleCertificateEnabled(alias, enabled)) },
                        onDelete = { onIntent(CertificateIntent.DeleteCertificate(it.alias)) }
                    )
                }
                CertificateTab.DOMAIN_RULES -> {
                    MtlsRuleList(
                        rules = filteredRules,
                        onRemoveRule = { onIntent(CertificateIntent.RemoveRule(it)) }
                    )
                }
            }
        }
    }
}
