package com.devuloopers.knet.ui.desktop.certificate.trust

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.desktop.certificate.model.TrustInstallationState

/**
 * TrustWizard provides the installation setup process workflow wizard to register KNet Root CA.
 */
@Composable
public fun TrustWizard(
    state: TrustInstallationState,
    onInstallTrust: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Text(
            text = "Root Store Trust Wizard",
            color = KNetColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "To inspect encrypted HTTPS traffic, the OS and local JVM trust stores must trust KNet's dynamic intercepting CA key.",
            color = KNetColors.TextSecondary,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(12.dp))

        val buttonText = when (state) {
            TrustInstallationState.IDLE -> "Trust Certificate Authority"
            TrustInstallationState.INSTALLING -> "Integrating with system keystore..."
            TrustInstallationState.INSTALLED -> "Certificate Trusted Successfully!"
            TrustInstallationState.FAILED -> "Installation Failed. Retry?"
        }

        val buttonColor = if (state == TrustInstallationState.INSTALLED) KNetColors.SuccessGreen else KNetColors.ActiveBlue

        Button(
            onClick = onInstallTrust,
            enabled = state != TrustInstallationState.INSTALLING,
            colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
        ) {
            Text(text = buttonText, color = KNetColors.TextPrimary, fontSize = 12.sp)
        }
    }
}
