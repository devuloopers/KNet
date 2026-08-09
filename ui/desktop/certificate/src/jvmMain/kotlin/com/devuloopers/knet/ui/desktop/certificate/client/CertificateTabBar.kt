package com.devuloopers.knet.ui.desktop.certificate.client

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.tabs.KNetTab
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.certificate.model.CertificateTab

@Composable
fun CertificateTabBar(
    activeTab: CertificateTab,
    onTabSelected: (CertificateTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(36.dp)
            .background(themeColors.background),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KNetTab(
            title = "Client Certificates (mTLS)",
            selected = activeTab == CertificateTab.CLIENT_CERTS,
            onClick = { onTabSelected(CertificateTab.CLIENT_CERTS) }
        )
        Spacer(modifier = Modifier.width(8.dp))
        KNetTab(
            title = "Domain Rules",
            selected = activeTab == CertificateTab.DOMAIN_RULES,
            onClick = { onTabSelected(CertificateTab.DOMAIN_RULES) }
        )
    }
}
