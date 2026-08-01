package com.devuloopers.knet.ui.desktop.certificate.overview

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
import com.devuloopers.knet.ui.desktop.certificate.model.CaDetails

/**
 * CaDetailsPanel renders detailed key attributes, algorithm info and fingerprints for active CA.
 */
@Composable
public fun CaDetailsPanel(
    details: CaDetails,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(text = "Subject DN: ${details.subject}", fontSize = 11.sp, color = KNetColors.TextPrimary, fontWeight = FontWeight.SemiBold)
        Text(text = "Issuer DN: ${details.issuer}", fontSize = 11.sp, color = KNetColors.TextSecondary, modifier = Modifier.padding(top = 2.dp))
        Text(text = "Serial: ${details.serialNumber}", fontSize = 11.sp, color = KNetColors.TextSecondary, modifier = Modifier.padding(top = 2.dp))
        Text(text = "Expiry Date: ${details.validUntil}", fontSize = 11.sp, color = KNetColors.TextSecondary, modifier = Modifier.padding(top = 2.dp))
        Text(text = "SHA-256 Fingerprint: ${details.sha256Fingerprint}", fontSize = 10.sp, color = KNetColors.TextMuted, modifier = Modifier.padding(top = 4.dp))
    }
}
