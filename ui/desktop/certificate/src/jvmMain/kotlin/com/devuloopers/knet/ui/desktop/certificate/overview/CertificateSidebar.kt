package com.devuloopers.knet.ui.desktop.certificate.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.certificate.model.CaDetails
import com.devuloopers.knet.ui.desktop.certificate.model.TrustInstallationState

@Composable
fun CertificateSidebar(
    caDetails: CaDetails,
    trustState: TrustInstallationState,
    onInstallTrustClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(themeColors.surface)
            .padding(16.dp)
    ) {
        SystemTrustHeader()

        Spacer(modifier = Modifier.height(8.dp))
        
        ActiveRootCaCard(caDetails = caDetails)
        
        Spacer(modifier = Modifier.height(4.dp))

        WindowsTrustStatusRow(
            trustState = trustState,
            onInstallClicked = onInstallTrustClick
        )
    }
}
