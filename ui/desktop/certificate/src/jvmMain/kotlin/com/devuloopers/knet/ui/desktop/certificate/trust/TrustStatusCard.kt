package com.devuloopers.knet.ui.desktop.certificate.trust

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.core.theme.KNetShapes
import com.devuloopers.knet.ui.desktop.certificate.model.TrustInstallationState

/**
 * TrustStatusCard renders the active integration status.
 */
@Composable
public fun TrustStatusCard(
    state: TrustInstallationState,
    modifier: Modifier = Modifier
) {
    val statusText = when (state) {
        TrustInstallationState.IDLE -> "Untrusted (Action Required)"
        TrustInstallationState.INSTALLING -> "Trust Installation in Progress"
        TrustInstallationState.INSTALLED -> "Trusted & Secure"
        TrustInstallationState.FAILED -> "Trust Store Write Rejected"
    }

    val color = when (state) {
        TrustInstallationState.INSTALLED -> KNetColors.SuccessGreen
        TrustInstallationState.INSTALLING -> KNetColors.WarningOrange
        else -> KNetColors.ErrorRed
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(KNetColors.SurfaceDark, KNetShapes.Medium)
            .padding(12.dp)
    ) {
        Text(text = "Keystore Trust Status", fontSize = 11.sp, color = KNetColors.TextSecondary)
        Text(
            text = statusText,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
