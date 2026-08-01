package com.devuloopers.knet.ui.desktop.certificate.viewer

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
 * FingerprintSection displays SHA1 and SHA256 fingerprints.
 */
@Composable
public fun FingerprintSection(
    details: CaDetails,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(text = "Fingerprints", color = KNetColors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(text = "SHA-1: ${details.sha1Fingerprint}", color = KNetColors.TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
        Text(text = "SHA-256: ${details.sha256Fingerprint}", color = KNetColors.TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
    }
}
