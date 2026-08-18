package com.devuloopers.knet.ui.desktop.certificate.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.application.port.certificate.CertificateAuthoritySummary
import com.devuloopers.knet.ui.desktop.certificate.model.HostPlatform
import com.devuloopers.knet.ui.desktop.certificate.model.TrustInstallationState

/**
 * Sidebar dashboard column displaying Root CA status and system trust installer.
 */
@Composable
fun CertificateSidebar(
    caDetails: CertificateAuthoritySummary,
    trustState: TrustInstallationState,
    platform: HostPlatform = HostPlatform.current(),
    onInstallTrustClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(themeColors.surface)
            .verticalScroll(scrollState)
            .padding(12.dp)
    ) {
        SystemTrustHeader()

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = themeColors.border,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                )
                .padding(10.dp)
        ) {
            Column {
                Text(
                    text = "TRUST STATUS",
                    style = typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = themeColors.textSecondary,
                    maxLines = 1,
                    softWrap = false
                )

                Spacer(modifier = Modifier.height(10.dp))
                
                ActiveRootCaCard(
                    caDetails = caDetails,
                    trustState = trustState
                )
                
                Spacer(modifier = Modifier.height(10.dp))

                SystemTrustStatusRow(
                    platform = platform,
                    trustState = trustState,
                    onInstallClicked = onInstallTrustClick
                )

                Spacer(modifier = Modifier.height(10.dp))

                ClientCertificatesInfoCard()
            }
        }
    }
}
