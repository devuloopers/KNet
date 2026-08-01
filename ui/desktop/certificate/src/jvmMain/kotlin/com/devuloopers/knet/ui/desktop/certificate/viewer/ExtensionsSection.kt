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

/**
 * ExtensionsSection displays X.509 V3 certificate extensions such as Key Usage and SANs.
 */
@Composable
public fun ExtensionsSection(
    sanEntries: List<String>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(text = "Extensions", color = KNetColors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(text = "Key Usage: Digital Signature, Key Encipherment, Certificate Signing", color = KNetColors.TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
        Text(text = "Subject Alternative Names (SANs):", color = KNetColors.TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
        sanEntries.forEach { san ->
            Text(text = " • $san", color = KNetColors.TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(start = 6.dp))
        }
    }
}
