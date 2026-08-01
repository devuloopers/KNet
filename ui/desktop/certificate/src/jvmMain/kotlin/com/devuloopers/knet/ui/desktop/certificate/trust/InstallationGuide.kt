package com.devuloopers.knet.ui.desktop.certificate.trust

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
 * InstallationGuide renders platform-specific manuals (Windows Certmgr, macOS Keychain Access, Linux trust anchoring).
 */
@Composable
public fun InstallationGuide(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Text(
            text = "Manual Installation Reference",
            color = KNetColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "• Windows: Export root.pem. Run certmgr.msc -> Place under 'Trusted Root Certification Authorities'.\n" +
                   "• macOS: Drag cert into 'Keychain Access' app under 'System' keychain. Right-click -> Get Info -> Select 'Always Trust'.\n" +
                   "• Linux (Ubuntu/Debian): Copy cert to /usr/local/share/ca-certificates/. Run sudo update-ca-certificates.",
            color = KNetColors.TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
