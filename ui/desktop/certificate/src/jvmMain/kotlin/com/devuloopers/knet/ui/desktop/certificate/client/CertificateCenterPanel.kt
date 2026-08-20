package com.devuloopers.knet.ui.desktop.certificate.client

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.certificate.model.CertificateIntent
import com.devuloopers.knet.ui.desktop.certificate.model.CertificateState
import com.devuloopers.knet.ui.desktop.certificate.model.CertificateTab
import com.devuloopers.knet.ui.desktop.certificate.mtls.MtlsRuleList
import com.devuloopers.knet.ui.core.components.button.KNetIconButton
import com.devuloopers.knet.ui.core.components.progress.LinearProgress

@Composable
fun CertificateCenterPanel(
    uiState: CertificateState,
    onIntent: (CertificateIntent) -> Unit,
    showTrustAction: Boolean = false,
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

        // Action Bar (Search + Refresh + Import/Add buttons)
        CertificateActionBar(
            activeTab = uiState.activeTab,
            searchQuery = uiState.searchQuery,
            onSearchChange = { onIntent(CertificateIntent.Search(it)) },
            onImportClick = { onIntent(CertificateIntent.SetImportDialogVisible(true)) },
            onAddRuleClick = { onIntent(CertificateIntent.SetRuleDialogVisible(true)) },
            onRefreshClick = { onIntent(CertificateIntent.Refresh) },
            showTrustAction = showTrustAction,
            onTrustClick = { onIntent(CertificateIntent.SetTrustDrawerVisible(true)) },
            enabled = uiState.activeOperation == null,
        )

        if (uiState.activeOperation != null) {
            LinearProgress(modifier = Modifier.fillMaxWidth().height(2.dp))
        }

        CertificateMessageBanner(
            errorMessage = uiState.errorMessage,
            informationMessage = uiState.informationMessage,
            onDismiss = { onIntent(CertificateIntent.ClearMessage) },
        )

        // Summary Metrics Bar
        CertificateMetricsBar(
            certificates = uiState.clientCertificates,
            mtlsRules = uiState.mtlsRules
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            when (uiState.activeTab) {
                CertificateTab.CLIENT_CERTS -> {
                    if (filteredCerts.isEmpty()) {
                        CertificateEmptyState(
                            onImportClick = { onIntent(CertificateIntent.SetImportDialogVisible(true)) }
                        )
                    } else {
                        ClientCertificateList(
                            certificates = filteredCerts,
                            selectedCertificateAlias = uiState.selectedCertificateAlias,
                            onSelect = { onIntent(CertificateIntent.SelectCertificate(it.alias)) },
                            onToggleEnabled = { alias, enabled -> onIntent(CertificateIntent.ToggleCertificateEnabled(alias, enabled)) },
                            onDelete = { onIntent(CertificateIntent.RequestDeleteCertificate(it.alias)) },
                            actionsEnabled = uiState.activeOperation == null,
                        )
                    }
                }
                CertificateTab.DOMAIN_RULES -> {
                    MtlsRuleList(
                        rules = filteredRules,
                        onRemoveRule = { onIntent(CertificateIntent.RequestRemoveRule(it)) },
                        onEditRule = { onIntent(CertificateIntent.SetRuleDialogVisible(true, it)) },
                        onToggleEnabled = { rule, enabled ->
                            onIntent(CertificateIntent.ToggleRuleEnabled(rule, enabled))
                        },
                        actionsEnabled = uiState.activeOperation == null,
                    )
                }
            }
        }
    }
}

@Composable
private fun CertificateMessageBanner(
    errorMessage: String?,
    informationMessage: String?,
    onDismiss: () -> Unit,
) {
    val message = errorMessage ?: informationMessage ?: return
    val colors = KNetTheme.colors
    val isError = errorMessage != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isError) colors.semantic.error.copy(alpha = 0.12f)
                else colors.accent.copy(alpha = 0.10f)
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isError) Icons.Default.ErrorOutline else Icons.Default.Info,
            contentDescription = null,
            tint = if (isError) colors.semantic.error else colors.accent,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = message,
            style = KNetTheme.typography.bodySmall,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        KNetIconButton(
            onClick = onDismiss,
            icon = Icons.Default.Close,
            contentDescription = "Dismiss message",
            size = 28.dp,
            iconSize = 16.dp,
            tint = colors.textSecondary,
        )
    }
}
