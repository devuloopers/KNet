package com.devuloopers.knet.ui.desktop.certificate.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.domain.util.HostPlatform
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.certificate.model.CaDetails
import com.devuloopers.knet.ui.desktop.certificate.model.TrustInstallationState

/**
 * Sidebar dashboard column displaying Root CA status and system trust installer.
 */
@Composable
fun CertificateSidebar(
    caDetails: CaDetails,
    trustState: TrustInstallationState,
    platform: HostPlatform = HostPlatform.current(),
    onInstallTrustClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(themeColors.surface)
            .padding(16.dp)
    ) {
        SystemTrustHeader()

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = themeColors.border,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                )
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "TRUST STATUS",
                    style = typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = themeColors.textSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))
                
                ActiveRootCaCard(
                    caDetails = caDetails,
                    trustState = trustState
                )
                
                Spacer(modifier = Modifier.height(12.dp))

                SystemTrustStatusRow(
                    platform = platform,
                    trustState = trustState,
                    onInstallClicked = onInstallTrustClick
                )

                Spacer(modifier = Modifier.height(12.dp))

                ClientCertificatesInfoCard()
            }
        }
    }
}
