package com.devuloopers.knet.ui.desktop.inspector.overview

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
import com.devuloopers.knet.ui.desktop.inspector.model.TransactionOverview

/**
 * Metadata card displaying host IP, port, TLS version, cipher suite, and compression.
 */
@Composable
public fun MetadataCard(
    overview: TransactionOverview,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(KNetColors.SurfaceDark, KNetShapes.Medium)
            .padding(10.dp)
    ) {
        Text("Metadata", color = KNetColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("Host: ${overview.host}", color = KNetColors.TextSecondary, fontSize = 11.sp)
        Text("IP: ${overview.ipAddress}:${overview.port}", color = KNetColors.TextSecondary, fontSize = 11.sp)
        Text("Protocol: ${overview.protocol}", color = KNetColors.TextSecondary, fontSize = 11.sp)
        Text("TLS: ${overview.tlsVersion} (${overview.cipherSuite})", color = KNetColors.TextSecondary, fontSize = 11.sp)
    }
}
