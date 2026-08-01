package com.devuloopers.knet.ui.desktop.inspector.tls

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.theme.KNetColors

/**
 * Cipher suite parameters view composable.
 */
@Composable
public fun CipherSuiteView(
    cipherSuite: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text("Cipher Suite: $cipherSuite", color = KNetColors.TextPrimary, fontSize = 11.sp)
    }
}
