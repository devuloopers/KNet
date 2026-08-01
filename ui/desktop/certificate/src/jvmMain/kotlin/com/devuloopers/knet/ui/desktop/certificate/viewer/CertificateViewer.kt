package com.devuloopers.knet.ui.desktop.certificate.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.core.theme.KNetShapes
import com.devuloopers.knet.ui.desktop.certificate.model.CaDetails

/**
 * CertificateViewer brings Fingerprints, Extensions and Subjects panels into a unified details sheet.
 */
@Composable
public fun CertificateViewer(
    details: CaDetails,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(KNetColors.SurfaceDark, KNetShapes.Medium)
            .padding(16.dp)
    ) {
        Text(
            text = "X.509 Certificate Inspector",
            color = KNetColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        SubjectIssuerSection(subject = details.subject, issuer = details.issuer)
        Spacer(modifier = Modifier.height(8.dp))
        FingerprintSection(details = details)
        Spacer(modifier = Modifier.height(8.dp))
        ExtensionsSection(sanEntries = listOf("DNS:localhost", "IP Address:127.0.0.1"))
    }
}
