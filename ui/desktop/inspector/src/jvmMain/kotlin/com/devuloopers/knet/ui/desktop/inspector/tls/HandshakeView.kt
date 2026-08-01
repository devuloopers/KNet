package com.devuloopers.knet.ui.desktop.inspector.tls

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.theme.KNetColors

/**
 * TLS Handshake parameters view composable.
 */
@Composable
public fun HandshakeView(
    tlsVersion: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text("Handshake Protocol: $tlsVersion", color = KNetColors.TextPrimary, fontSize = 11.sp)
        Text("ALPN: h2, http/1.1", color = KNetColors.TextSecondary, fontSize = 11.sp)
    }
}
