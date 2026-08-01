package com.devuloopers.knet.ui.desktop.inspector.tls

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.theme.KNetColors

/**
 * TLS Certificate chain view composable.
 */
@Composable
public fun CertificateChainView(
    host: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text("Subject: CN=$host", color = KNetColors.TextPrimary, fontSize = 11.sp)
        Text("Issuer: CN=KNet Root CA", color = KNetColors.TextSecondary, fontSize = 11.sp)
    }
}
