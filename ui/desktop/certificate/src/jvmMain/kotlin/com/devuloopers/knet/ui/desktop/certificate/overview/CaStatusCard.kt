package com.devuloopers.knet.ui.desktop.certificate.overview

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
import com.devuloopers.knet.ui.desktop.certificate.model.CaStatus

/**
 * CaStatusCard renders visual badge indicator of Root CA health.
 */
@Composable
public fun CaStatusCard(
    status: CaStatus,
    modifier: Modifier = Modifier
) {
    val statusText = when (status) {
        CaStatus.AVAILABLE -> "Active CA Authority Generated"
        CaStatus.MISSING -> "Certificate Authority Missing"
        CaStatus.EXPIRED -> "Root Certificate Authority Expired"
        CaStatus.INVALID -> "Root CA Signature Mismatched"
        CaStatus.INSTALLATION_REQUIRED -> "Not Trusted by System Root Store"
    }

    val color = when (status) {
        CaStatus.AVAILABLE -> KNetColors.SuccessGreen
        CaStatus.INSTALLATION_REQUIRED -> KNetColors.WarningOrange
        else -> KNetColors.ErrorRed
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(KNetColors.SurfaceDark, KNetShapes.Medium)
            .padding(12.dp)
    ) {
        Text(text = "Root CA Authority Status", fontSize = 11.sp, color = KNetColors.TextSecondary)
        Text(
            text = statusText,
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
