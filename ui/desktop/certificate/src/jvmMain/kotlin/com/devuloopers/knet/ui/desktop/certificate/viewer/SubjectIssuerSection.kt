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
 * SubjectIssuerSection displays DN details for subject and issuer fields.
 */
@Composable
public fun SubjectIssuerSection(
    subject: String,
    issuer: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(text = "Subject & Issuer", color = KNetColors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(text = "Subject: $subject", color = KNetColors.TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
        Text(text = "Issuer: $issuer", color = KNetColors.TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
    }
}
